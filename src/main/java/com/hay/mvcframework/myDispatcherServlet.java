package com.hay.mvcframework;

import com.hay.mvcframework.annotation.MyAutowired;
import com.hay.mvcframework.annotation.MyController;
import com.hay.mvcframework.annotation.MyRequestMapping;
import com.hay.mvcframework.annotation.MyService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @title: mySpringMvc
 * @Author HuangYan
 * @Date: 2020/7/9 21:43
 * @Version 1.0
 */
public class myDispatcherServlet extends HttpServlet {

    //创建ioc容器
    private Map<String,Object> ioc = new HashMap<>();
    //
    private Properties applicationContext = new Properties();
    //用于存储需要自动注入ioc容器中的类
    private List<String> classNames = new ArrayList<>();
    //用于存放访问路径
    private Map<String,Object> handlerMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //6. 完成url调度
        try {
            doDispatcher(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception Detail :" + Arrays.toString(e.getStackTrace()));
        }
    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        //1. 加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2. 扫描相关的类
        doScanner(applicationContext.getProperty("scanPackage"));
        //3. 实例化相关的类，并且缓存到ioc容器中
        doInstance();
        //4. 完成依赖注入
        doAutowired();
        //5. 初始化映射关系HandlerMapping
        doInitHandlerMapping();
        System.out.println("My Spring Framework init...");
    }


    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String requestURI = req.getRequestURI();
        String contextPath = req.getContextPath();
        requestURI = requestURI.replaceAll(contextPath,"").replaceAll("/+","/");
        if (!handlerMapping.containsKey(requestURI)){
            resp.getWriter().write("404 not found");
            return;
        }
        Method method = (Method) this.handlerMapping.get(requestURI);
        String beanName = firstClassCharToLowerCase(method.getDeclaringClass().getSimpleName());
        Map<String,String[]> parameterMap = req.getParameterMap();
        Object[] args = {req,resp,parameterMap.get("name")[0]};
        method.invoke(ioc.get(beanName),args);
    }

    private void doInitHandlerMapping() {
        if (ioc.isEmpty()){return;}
        //遍历ioc容器中的bean对象
        for (Map.Entry<String,Object> entry : ioc.entrySet()) {
            //获取类
            Class clazz = entry.getValue().getClass();
            String baseUrl = "";
            //如果Controller上有RequestMapping注解,则需要加上此前缀
            if (clazz.isAnnotationPresent(MyRequestMapping.class)){
                MyRequestMapping baseMapping = (MyRequestMapping) clazz.getAnnotation(MyRequestMapping.class);
                baseUrl = baseMapping.value();
            }
            //遍历每个方法，是否存在MyRequestMapping注解
            for (Method method:clazz.getMethods()) {
                //不存在，跳过
                if (!method.isAnnotationPresent(MyRequestMapping.class)){continue;}
                String url = "";
                MyRequestMapping urlMapping = (MyRequestMapping)method.getAnnotation(MyRequestMapping.class);
                //拼接访问路径
                url = ("/" + baseUrl + "/" + urlMapping.value()).replaceAll("/+","/");
                //存放在缓存中
                handlerMapping.put(url,method);
            }
        }
    }

    private void doAutowired() {
        if (ioc.isEmpty()){
            return;
        }
        for (Map.Entry<String,Object> entry : ioc.entrySet()){
            //获取类中的所有属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            //遍历字段，是否书写了MyAutowired
            for (Field field: fields) {
                //如果没有，则跳过
                if (!field.isAnnotationPresent(MyAutowired.class)){
                    continue;
                }
                //如果存在，则注入对象
                MyAutowired autowired = field.getAnnotation(MyAutowired.class);
                //获取MyAutowired注解中的默认bean的名字
                String beanName = autowired.value();
                //如果为空，则使用此字段类型作为bean的名字
                if ("".equals(beanName)){
                    // 如果是接口，则beanName 为全限定名
                    if (field.getType().isInterface()){
                        beanName = field.getType().getName();
                    } else {
                        // 否则为类名，并且首字母小写
                        beanName = field.getType().getSimpleName();
                        beanName = firstClassCharToLowerCase(beanName);
                    }
                }
                try {
                    //开启暴力强制访问，比如说用private修饰的私有变量
                    field.setAccessible(true);
                    //!!!!!!传说的中的依赖注入
                    //将指定对象中的此字段的值设置为新值
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doInstance() {
        //如果classNames为空，则没有需要加入ioc容器中的类
        if (classNames.isEmpty()){
            return;
        }
        try{
            //遍历classNames，实例化每个对象并放入ioc容器中
            for (String className: classNames) {
                Class clazz = Class.forName(className);
                //如果类上有MyController注解
                if(clazz.isAnnotationPresent(MyController.class)){
                    //通过反射获取到对象
                    Object instance = clazz.newInstance();
                    //获取此对象的类名
                    String beanName = firstClassCharToLowerCase(clazz.getSimpleName());
                    //将对象放入ioc容器中
                    ioc.put(beanName,instance);
                }
                //如果类上有MyService注解
                if (clazz.isAnnotationPresent(MyService.class)){
                    //通过反射获取到对象
                    Object instance = clazz.newInstance();
                    //获取此对象的类名
                    String beanName = firstClassCharToLowerCase(clazz.getSimpleName());
                    //自定义beanName;如果注解中给定了默认的beanName，则用此作为对象的key
                    MyService service = (MyService) clazz.getAnnotation(MyService.class);
                    if (!"".equals(service.value())){
                        beanName = service.value();
                    }
                    //将对象放入ioc容器中
                    ioc.put(beanName,instance);
                    //将此对象实现的所有接口也放入ioc容器，以此对象作为bean
                    for (Class i: clazz.getInterfaces()){
                        if (ioc.containsKey(i.getName())){
                            throw new Exception("This beanName is exists!");
                        }
                        System.out.println("对象实现的接口"+i.getName());
                        ioc.put(i.getName(),instance);
                    }
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private String firstClassCharToLowerCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private void doScanner(String scanPackage) {
        //获取需要扫描包的地址，将com.hay.demo=>com/hay/demo这样的路径
        URL url = this.getClass().getClassLoader().
                getResource("/" + scanPackage.replaceAll("\\.","/"));
        //根据路径创建一个File文件对象
        File classPath = new File(url.getFile());
        //遍历此对象文件中的文件
        for (File file: classPath.listFiles()) {
            String className = (scanPackage + "." + file.getName().replace(".class",""));
            //如果是文件夹则递归继续遍历
            if (file.isDirectory()){
                doScanner(className);
                continue;
            }
            //如果不是类(以.class结尾的),则不需要放入ioc容器中
            if(!file.getName().endsWith(".class")){
                continue;
            }
            //将需要加载到ioc容器中的类名加入到classNames中
            classNames.add(className);  // com.hay.demo.controller.DemoController
        }
    }

    private void doLoadConfig(String contextConfigLocation) {
        //将applicationContext.properties流输入
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        //使用properties加载此输入流
        try {
            applicationContext.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //如果输入流不给空，则关闭流
            if (null != is){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
