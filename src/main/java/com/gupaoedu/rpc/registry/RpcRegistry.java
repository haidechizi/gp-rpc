package com.gupaoedu.rpc.registry;

import com.gupaoedu.rpc.annotation.RpcService;
import com.gupaoedu.rpc.protocol.InvokeProtocal;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.util.concurrent.EventExecutorGroup;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RpcRegistry {

    private int port;

    public RpcRegistry(int port) {
        this.port = port;
    }

    public void start() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workGroup = new NioEventLoopGroup();
        try {

            ServerBootstrap bootstrap = new ServerBootstrap();

            bootstrap.group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ChannelPipeline pipeline = socketChannel.pipeline();
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,
                                    0, 4, 0, 4));
                            pipeline.addLast(new LengthFieldPrepender(4));
                            pipeline.addLast(new ObjectEncoder());
                            pipeline.addLast(new ObjectDecoder(Integer.MAX_VALUE, ClassResolvers.cacheDisabled(null)));
                            pipeline.addLast(new RpcServerHandler());
                        }
                    }).option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.SO_SNDBUF, 3 * 1024)
                    .option(ChannelOption.SO_RCVBUF, 3 * 1024)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.bind(this.port).sync();
            System.out.println("服务启动成功");

            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }

    }

    private class RpcServerHandler extends ChannelInboundHandlerAdapter {

        private static final String basePackage = "com.gupaoedu.rpc";

        private List<String> classNames = new ArrayList<>();

        private Map<String, Object> serviceMapping = new ConcurrentHashMap<>();

        public RpcServerHandler() {


            doScanner(basePackage);

            initService();
        }

        private void initService() {
            if (classNames.isEmpty()) {
                return;
            }

            for (String className : classNames) {
                try {
                    Class<?> clazz = Class.forName(className);
                    if (!clazz.isAnnotationPresent(RpcService.class)) {
                        continue;
                    }

                    RpcService rpcService = clazz.getAnnotation(RpcService.class);

                    Class<?> interfaceClazz = rpcService.clazz();
                    String serviceName = interfaceClazz.getName();
                    if (!"".equals(rpcService.group())) {
                        serviceName += "_group=" + rpcService.group();
                    }

                    if (!"".equals(rpcService.version())) {
                        serviceName += "_version=" + rpcService.version();
                    }

                    serviceMapping.put(serviceName, clazz.newInstance());


                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                }
            }
        }

        private void doScanner(String basePackage) {
            URL url = this.getClass().getClassLoader().getResource(basePackage.replace(".", "/"));
            File file = new File(url.getFile());
            for (File f : file.listFiles()) {
                if (f.isDirectory()) {
                    doScanner(basePackage + "." + f.getName());
                } else {
                    String className = basePackage + "." + f.getName().replace(".class", "");
                    classNames.add(className);
                }
            }
        }


        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            InvokeProtocal invokeProtocal = (InvokeProtocal) msg;
            String serviceName = invokeProtocal.getClassName();
            if (!"".equals(invokeProtocal.getGroup())) {
                serviceName += "_group=" + invokeProtocal.getGroup();
            }

            if (!"".equals(invokeProtocal.getVersion())) {
                serviceName += "_version=" + invokeProtocal.getVersion();
            }

            if (!serviceMapping.containsKey(serviceName)) {
                throw new RuntimeException("there is no provider active");
            }

            Object service = serviceMapping.get(serviceName);
            Method method = service.getClass().getMethod(invokeProtocal.getMethodName(), invokeProtocal.getParameterTypes());
            Object obj = method.invoke(service, invokeProtocal.getParameters());

            ctx.writeAndFlush(obj);
            ctx.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
    }

    public static void main(String[] args) {
        new RpcRegistry(8888).start();
    }
}
