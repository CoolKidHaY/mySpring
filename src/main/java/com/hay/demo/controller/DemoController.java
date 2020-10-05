package com.hay.demo.controller;

import com.hay.demo.service.DemoService;
import com.hay.demo.service.impl.DemoServiceImpl;
import com.hay.mvcframework.annotation.MyAutowired;
import com.hay.mvcframework.annotation.MyController;
import com.hay.mvcframework.annotation.MyRequestMapping;
import com.hay.mvcframework.annotation.MyRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @title: demoController
 * @Author HuangYan
 * @Date: 2020/7/10 16:40
 * @Version 1.0
 */
@MyController
@MyRequestMapping("/demo")
public class DemoController {

    @MyAutowired
    DemoService demoService;

    @MyRequestMapping("/say")
    public void sayHello(HttpServletRequest request, HttpServletResponse response,@MyRequestParam String name){
        String s = demoService.sayHello(name);
        try {
            response.getWriter().write(s);
        } catch (IOException e) {
            e.getCause();
            e.printStackTrace();
        }
    }
}
