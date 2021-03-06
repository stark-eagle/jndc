package jndc.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DecoderException;
import jndc.core.NDCMessageProtocol;
import jndc.core.TcpServiceDescription;
import jndc.core.UniqueBeanManage;
import jndc.core.config.ClientConfig;
import jndc.core.config.UnifiedConfiguration;
import jndc.core.message.RegistrationMessage;
import jndc.core.message.UserError;
import jndc.exception.ConnectionOpenFailException;
import jndc.exception.SecreteDecodeFailException;
import jndc.utils.ApplicationExit;
import jndc.utils.InetUtils;
import jndc.utils.ObjectSerializableUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class JNDCClientMessageHandle extends SimpleChannelInboundHandler<NDCMessageProtocol> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private JNDCClient client;

    public static final String NAME = "NDC_CLIENT_HANDLE";

    private ChannelHandlerContext ctx;

    private volatile boolean reConnectTag = true;

    public JNDCClientMessageHandle(JNDCClient jndcClient) {
        this.client = jndcClient;
    }

    public void sendRegisterToServer(int localPort, int serverPort) {
        RegistrationMessage registrationMessage = new RegistrationMessage();
        //  registrationMessage.setEquipmentId(InetUtils.uniqueInetTag);
        byte[] bytes = ObjectSerializableUtils.object2bytes(registrationMessage);


        NDCMessageProtocol tqs = NDCMessageProtocol.of(InetUtils.localInetAddress, InetUtils.localInetAddress, 0, serverPort, localPort, NDCMessageProtocol.MAP_REGISTER);
        tqs.setData(bytes);
        ctx.writeAndFlush(tqs);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        UnifiedConfiguration unifiedConfiguration = UniqueBeanManage.getBean(UnifiedConfiguration.class);
        ClientConfig clientConfig = unifiedConfiguration.getClientConfig();

        JNDCClientConfigCenter jndcClientConfigCenter = UniqueBeanManage.getBean(JNDCClientConfigCenter.class);


        if (clientConfig == null || clientConfig.getClientServiceDescriptions() == null) {
            logger.error("can not load mapping config");
            return;
        }


        RegistrationMessage registrationMessage = new RegistrationMessage();
        registrationMessage.setAuth(unifiedConfiguration.getSecrete());

        List<TcpServiceDescription> tcpServiceDescriptions = new ArrayList<>();

        clientConfig.getClientServiceDescriptions().forEach(x -> {
            if (x.isServiceEnable()) {
                tcpServiceDescriptions.add(x.toTcpServiceDescription());

                //init the support service
                jndcClientConfigCenter.initService(x);

            } else {
                logger.info("ignore the mapping:" + x.getServiceName());
            }
        });

        registrationMessage.setTcpServiceDescriptions(tcpServiceDescriptions);
        byte[] bytes = ObjectSerializableUtils.object2bytes(registrationMessage);


        InetAddress unused = InetAddress.getLocalHost();
        NDCMessageProtocol tqs = NDCMessageProtocol.of(unused, unused, NDCMessageProtocol.UN_USED_PORT, NDCMessageProtocol.UN_USED_PORT, NDCMessageProtocol.UN_USED_PORT, NDCMessageProtocol.MAP_REGISTER);

        tqs.setData(bytes);
        ctx.writeAndFlush(tqs);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, NDCMessageProtocol ndcMessageProtocol) throws Exception {
        byte type = ndcMessageProtocol.getType();

        try {


            if (type == NDCMessageProtocol.CHANNEL_HEART_BEAT) {
                //todo CHANNEL_HEART_BEAT
                //just accept
                logger.info("get heart beat from server");

            }

            if (type == NDCMessageProtocol.TCP_DATA) {
                //todo TCP_DATA
                JNDCClientConfigCenter bean = UniqueBeanManage.getBean(JNDCClientConfigCenter.class);
                bean.addMessageToReceiveQueue(ndcMessageProtocol);
                return;
            }


            if (type == NDCMessageProtocol.TCP_ACTIVE) {
                //todo TCP_ACTIVE
                JNDCClientConfigCenter bean = UniqueBeanManage.getBean(JNDCClientConfigCenter.class);
                bean.addMessageToReceiveQueue(ndcMessageProtocol);
                return;
            }

            if (type == NDCMessageProtocol.MAP_REGISTER) {
                //todo MAP_REGISTER


                //print msg
                RegistrationMessage object = ndcMessageProtocol.getObject(RegistrationMessage.class);
                logger.info(object.getMessage());


                //register channel,client just hold one channelHandlerContext
                JNDCClientConfigCenter bean = UniqueBeanManage.getBean(JNDCClientConfigCenter.class);
                bean.registerMessageChannel(channelHandlerContext);

                InetAddress unused = InetAddress.getLocalHost();
                final NDCMessageProtocol tqs = NDCMessageProtocol.of(unused, unused, NDCMessageProtocol.UN_USED_PORT, NDCMessageProtocol.UN_USED_PORT, NDCMessageProtocol.UN_USED_PORT, NDCMessageProtocol.CHANNEL_HEART_BEAT);

                //send heart beat
                EventLoop eventExecutors = channelHandlerContext.channel().eventLoop();
                eventExecutors.scheduleAtFixedRate(() -> {
                    bean.addMessageToSendQueue(tqs);
                }, 0, 60, TimeUnit.SECONDS);


                return;

            }

            if (type == NDCMessageProtocol.CONNECTION_INTERRUPTED) {
                //todo CONNECTION_INTERRUPTED

                JNDCClientConfigCenter bean = UniqueBeanManage.getBean(JNDCClientConfigCenter.class);
                bean.shutDownClientServiceProvider(ndcMessageProtocol);
                return;
            }

            if (type == NDCMessageProtocol.NO_ACCESS) {
                //todo NO_ACCESS
                reConnectTag = false;//not restart
                channelHandlerContext.close();
                channelHandlerContext.channel().eventLoop().shutdownGracefully().addListener(x -> {
                    if (x.isSuccess()) {
                        logger.error("register auth fail, the client will close later...");
                    } else {
                        logger.error("shutdown fail");
                    }
                });
                return;
            }

            if (type == NDCMessageProtocol.USER_ERROR) {
                //todo USER_ERROR
                UserError userError = ndcMessageProtocol.getObject(UserError.class);
                logger.error(userError.toString());
                return;
            }

            if (type == NDCMessageProtocol.UN_CATCHABLE_ERROR) {
                //todo UN_CATCHABLE_ERROR
                logger.error(new String(ndcMessageProtocol.getData()));
                return;
            }

        } catch (Exception e) {
            NDCMessageProtocol copy = ndcMessageProtocol.copy();
            copy.setType(NDCMessageProtocol.CONNECTION_INTERRUPTED);
            copy.setData(NDCMessageProtocol.BLANK);
            UniqueBeanManage.getBean(JNDCClientConfigCenter.class).addMessageToSendQueue(copy);

            logger.error(type+": client get a unCatchable Error:" + e);
        }


    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (reConnectTag) {
            logger.info("client connection interrupted, will restart on 5 second later");
            TimeUnit.SECONDS.sleep(5);
            EventLoop eventExecutors = ctx.channel().eventLoop();
            client.createClient(eventExecutors);
        }

    }


    @Override
    public void exceptionCaught(ChannelHandlerContext channelHandlerContext, Throwable cause) throws Exception {
        if (cause instanceof DecoderException) {
            if (cause.getCause() instanceof SecreteDecodeFailException) {
                //auth fail
                logger.error("secrete check error when decode,please check the secrete later...");
                ApplicationExit.exit();

            }
            channelHandlerContext.close();
            logger.error("unCatchable client error：" + cause.getMessage());
        }
    }

}
