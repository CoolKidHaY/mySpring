package com.hay.demo.service.impl;

import com.hay.demo.service.DemoService;
import com.hay.mvcframework.annotation.MyService;

import java.util.HashMap;

/**
 * @title: demoServiceImpl
 * @Author HuangYan
 * @Date: 2020/7/10 16:42
 * @Version 1.0
 */
@MyService
public class DemoServiceImpl implements DemoService {
    @Override
    public String sayHello(String name) {
        return "hello " + name +"! How are you?";
    }
}
