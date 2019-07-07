package com.gupaoedu;

import com.gupaoedu.rpc.api.IHelloService;
import com.gupaoedu.rpc.proxy.RpcProxy;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        IHelloService iHelloService = new RpcProxy().getProxy(IHelloService.class);
        String result = iHelloService.sayHello("Tom");

        System.out.println(result);
    }
}
