package com.gupaoedu.rpc.protocol;

import lombok.Data;

import java.io.Serializable;


@Data
public class InvokeProtocal implements Serializable {
    private static final long serialVersionUID = -3336945071870946667L;

    private String className;
    private String methodName;
    private Object[] parameters;
    private Class<?>[] parameterTypes;
    private String version;
    private String group;


}
