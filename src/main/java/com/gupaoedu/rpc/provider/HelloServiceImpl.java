package com.gupaoedu.rpc.provider;

import com.gupaoedu.rpc.annotation.RpcService;
import com.gupaoedu.rpc.api.IHelloService;

@RpcService(clazz = IHelloService.class)
public class HelloServiceImpl implements IHelloService {

    @Override
    public String sayHello(String name) {
        System.out.println("hello " + name);
        return "hello " + name;
    }
}
