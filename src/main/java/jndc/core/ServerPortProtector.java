package jndc.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jndc.server.NDCServerConfigCenter;
import jndc.server.ServerTCPDataHandle;
import jndc.utils.LogPrint;
import jndc.utils.UniqueInetTagProducer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerPortProtector  implements PortProtector{

    private NDCMessageProtocol registerMessage;

    private NDCServerConfigCenter ndcServerConfigCenter;

    private ServerBootstrap serverBootstrap;

    private EventLoopGroup eventLoopGroup;

    private volatile boolean appRunnable = false;

    private Map<String, ServerTCPDataHandle> faceTCPMap = new ConcurrentHashMap<>();//store tcp


    public ServerPortProtector() {
    }

    /**
     * 启动
     *
     * @param registerMessage
     */
    @Override
    public void start(NDCMessageProtocol registerMessage, NDCServerConfigCenter ndcServerConfigCenter) {
        if (appRunnable) {//just run once
            return;
        }

        this.ndcServerConfigCenter = ndcServerConfigCenter;
        this.registerMessage=registerMessage;


        //create  Initializer
        ChannelInitializer<Channel> channelInitializer = new ChannelInitializer<>() {
            @Override
            protected void initChannel(Channel channel) throws Exception {
                ChannelPipeline pipeline = channel.pipeline();


                //the callback  for connecting and closing event
                InnerHandlerCallBack innerHandlerCallBack = new InnerHandlerCallBack() {
                    @Override
                    public void registerHandler(String uniqueTag, ServerTCPDataHandle serverTCPDataHandle) {
                        ServerTCPDataHandle serverTCPDataHandle1 = faceTCPMap.get(uniqueTag);
                        if (serverTCPDataHandle1 != null) {
                            //todo impossible to this ,but just in case
                            serverTCPDataHandle1.close();
                        }
                        faceTCPMap.put(uniqueTag, serverTCPDataHandle);
                    }

                    @Override
                    public void unRegisterHandler(String uniqueTag) {
                        faceTCPMap.remove(uniqueTag);
                    }

                    @Override
                    public int getLocalPort() {
                        return registerMessage.getLocalPort();
                    }

                };




                //the handle of tcp data from user client
                ServerTCPDataHandle serverTCPDataHandle = new ServerTCPDataHandle(innerHandlerCallBack);

                pipeline.addFirst(ServerTCPDataHandle.NAME, serverTCPDataHandle);
            }
        };


        int serverPort = this.registerMessage.getServerPort();


        eventLoopGroup = NettyComponentConfig.getNioEventLoopGroup();

        serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)//
                .localAddress(new InetSocketAddress(serverPort))//　
                .childHandler(channelInitializer);

        ChannelFuture bind = serverBootstrap.bind().addListener(x -> {
            try{
                Object object = x.get();
                LogPrint.log("bind map port:" + serverPort);
                appRunnable = true;
                ndcServerConfigCenter.registerPortProtector(serverPort, this);
            }catch (Exception e){
                e.printStackTrace();
                LogPrint.log("port listen fail cause："+e);
            }
        });
    }


    /**
     * 关闭
     */
    @Override
    public void shutDown() {
        int serverPort = this.registerMessage.getServerPort();
        ndcServerConfigCenter.unRegisterPortProtector(serverPort);
        eventLoopGroup.shutdownGracefully().addListener(x -> {
            LogPrint.log("shut down  local port:" + serverPort);
            appRunnable = false;
        });
    }

    @Override
    public void receiveMessage(NDCMessageProtocol ndcMessageProtocol) {

        String s = UniqueInetTagProducer.get4Server(ndcMessageProtocol.getRemoteInetAddress(),ndcMessageProtocol.getRemotePort());
        ServerTCPDataHandle serverTCPDataHandle = faceTCPMap.get(s);
        if (serverTCPDataHandle == null) {
            //todo drop message
        } else {
            byte[] data = ndcMessageProtocol.getData();
            ByteBuf byteBuf = Unpooled.copiedBuffer(data);
            serverTCPDataHandle.writeMessage(byteBuf);
        }

    }

    public void shutDownTcpConnection(NDCMessageProtocol ndcMessageProtocol) {
        String s = UniqueInetTagProducer.get4Server(ndcMessageProtocol.getRemoteInetAddress(),ndcMessageProtocol.getRemotePort());
        ServerTCPDataHandle serverTCPDataHandle = faceTCPMap.get(s);
        if (serverTCPDataHandle == null) {
            //do nothing
        } else {
            faceTCPMap.remove(s);
            serverTCPDataHandle.close();
            LogPrint.log("close face connection cause local connection interrupted:"+s);
        }
    }

    public interface InnerHandlerCallBack {
        public void registerHandler(String uniqueTag, ServerTCPDataHandle serverTCPDataHandle);

        public void unRegisterHandler(String uniqueTag);

        public int getLocalPort();
    }
}
