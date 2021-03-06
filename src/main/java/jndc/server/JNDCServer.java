package jndc.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jndc.core.*;
import jndc.core.config.ServerConfig;
import jndc.core.config.UnifiedConfiguration;
import jndc.core.data_store.DBWrapper;
import jndc.utils.LogPrint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import web.core.FrontProjectLoader;
import web.core.WebServer;

import java.io.File;
import java.net.URL;

public class JNDCServer {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private EventLoopGroup eventLoopGroup = NettyComponentConfig.getNioEventLoopGroup();


    public JNDCServer() {
    }

    public void initBelongServerOnly() {
        ScheduledTaskCenter ipChecker = UniqueBeanManage.getBean(ScheduledTaskCenter.class);
        ipChecker.start();
    }


    public void createServer() {
        //do server init
        initBelongServerOnly();


        UnifiedConfiguration bean = UniqueBeanManage.getBean(UnifiedConfiguration.class);
        ServerConfig serverConfig = bean.getServerConfig();


        //reset service bind state
        DBWrapper<ServerPortBind> dbWrapper = DBWrapper.getDBWrapper(ServerPortBind.class);
        dbWrapper.customExecute("update server_port_bind set portEnable = 0", null);

        //deploy the server management api
        WebServer serverTest = new WebServer();
        serverTest.start();//start

        // confirm whether to deploy default static project
        // the management project will be deploy in managementApiPort
        if (serverConfig.isDeployFrontProject()) {
            //load inner front file
            String web = serverConfig.getFrontProjectPath();
            if (!web.endsWith(File.separator)) {
                web += File.separator;
            }
            FrontProjectLoader.jndcStaticProject = FrontProjectLoader.loadProject(web);
            LogPrint.info("deploy front management project");
        }


        ChannelInitializer<Channel> channelInitializer = new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel channel) throws Exception {
                ChannelPipeline pipeline = channel.pipeline();

                pipeline.addFirst(IPFilter.NAME, IPFilter.STATIC_INSTANCE);
                pipeline.addAfter(IPFilter.NAME, NDCPCodec.NAME, new NDCPCodec());
                pipeline.addAfter(NDCPCodec.NAME, SecreteCodec.NAME, new SecreteCodec());
                pipeline.addAfter(SecreteCodec.NAME, JNDCServerMessageHandle.NAME, new JNDCServerMessageHandle());
            }
        };

        ServerBootstrap b = new ServerBootstrap();
        b.group(eventLoopGroup)
                .channel(NioServerSocketChannel.class)//
                .localAddress(serverConfig.getInetSocketAddress())//　
                .childHandler(channelInitializer);

        b.bind().addListener(x -> {
            if (x.isSuccess()) {
                logger.info("bind admin : " + serverConfig.getInetSocketAddress() + " success");
            } else {
                logger.error("bind admin : " + serverConfig.getInetSocketAddress() + " fail");
            }

        });


    }


}
