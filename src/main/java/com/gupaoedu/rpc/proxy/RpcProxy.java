package com.gupaoedu.rpc.proxy;

import com.gupaoedu.rpc.annotation.RpcClient;
import com.gupaoedu.rpc.protocol.InvokeProtocal;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class RpcProxy {

    private static final String ip = "localhost";
    private static final int port = 8888;

    public <T> T getProxy(Class<?> clazz) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, new ClientProxyHandler(clazz));
    }

    private class ClientProxyHandler implements InvocationHandler {

        private Class<?> clazz;

        public ClientProxyHandler(Class<?> clazz) {
            this.clazz = clazz;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            if (!clazz.isAnnotationPresent(RpcClient.class)) {
                throw new RuntimeException("client should be annotation by RpcClient");
            }
            RpcClient rpcClient = clazz.getAnnotation(RpcClient.class);

            InvokeProtocal invokeProtocal = new InvokeProtocal();
            invokeProtocal.setClassName(clazz.getName());
            invokeProtocal.setGroup(rpcClient.group());
            invokeProtocal.setVersion(rpcClient.version());
            invokeProtocal.setMethodName(method.getName());
            invokeProtocal.setParameters(args);
            invokeProtocal.setParameterTypes(method.getParameterTypes());

            return processInvoke(invokeProtocal);

        }

        private Object processInvoke(InvokeProtocal invokeProtocal) {
            EventLoopGroup group = new NioEventLoopGroup();
            Bootstrap bootstrap = new Bootstrap();
            final ClientInvokeHandler clientInvokeHandler = new ClientInvokeHandler();
            try {

                bootstrap.group(group).channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel socketChannel) throws Exception {
                                ChannelPipeline pipeline = socketChannel.pipeline();
                                pipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                                        0, 4, 0, 4));
                                pipeline.addLast(new LengthFieldPrepender(4));
                                pipeline.addLast(new ObjectEncoder());
                                pipeline.addLast(new ObjectDecoder(Integer.MAX_VALUE, ClassResolvers.cacheDisabled(null)));
                                pipeline.addLast(clientInvokeHandler);
                            }
                        }).option(ChannelOption.TCP_NODELAY,true);
                ChannelFuture future = bootstrap.connect(ip, port).sync();
                future.channel().writeAndFlush(invokeProtocal).sync();
                future.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                group.shutdownGracefully();
            }
            return clientInvokeHandler.getResult();

        }

    }
}
