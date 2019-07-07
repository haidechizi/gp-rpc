package com.gupaoedu.rpc.api;


import com.gupaoedu.rpc.annotation.RpcClient;

@RpcClient
public interface IHelloService {

    public String sayHello(String name);
}
