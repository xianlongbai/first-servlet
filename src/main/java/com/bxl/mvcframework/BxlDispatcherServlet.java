package com.bxl.mvcframework;

import com.bxl.mvcframework.annotation.BxlAutowried;
import com.bxl.mvcframework.annotation.BxlController;
import com.bxl.mvcframework.annotation.BxlRequestMapping;
import com.bxl.mvcframework.annotation.BxlService;

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
 * mvc 的核心调度器
 *  https://www.jianshu.com/p/f57db8b29be9
 */
public class BxlDispatcherServlet extends HttpServlet {

    //用来取出配置文件信息
    public static final String LOCATION = "contextConfigLocation";
    //保存所有的配置信息
    private Properties prop = new Properties();
    //保存所有被扫描到的相关的类
    private List<String> classNames = new ArrayList<String>();
    //保存所有的初始化bean,IOC容器核心
    private Map<String,Object> ioc = new HashMap();
    //保存所有的url和方法的映射关系
    private Map<String, Method> handlerMapping = new HashMap();

    public BxlDispatcherServlet() {
        super();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //在doPost方法中再调用doDispach()方法
        try {
            doDispatch(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req,resp);
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (this.handlerMapping.isEmpty()) return;
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath, "").replaceAll("/+", "/");

        if (!handlerMapping.containsKey(url)) {
            resp.getWriter().write("404 not found!");
            return;
        }

        Map<String, String[]> params = req.getParameterMap();
        Method method = handlerMapping.get(url);
        //获取方法参数列表
        Class<?>[] parameterTypes = method.getParameterTypes();
        //获取请求的参数
        Map<String, String[]> parameterMap = req.getParameterMap();
        //保存参数
        Object[] paramValues = new Object[parameterTypes.length];
        //方法的参数列表
        for (int i = 0; i < parameterTypes.length; i++) {
            //根据参数名称做某些处理
            Class<?> parameterType = parameterTypes[i];
            if (parameterType == HttpServletRequest.class) {
                //参数类型已经明确，这边强转类型
                paramValues[i] = req;
                continue;
            } else if (parameterType == HttpServletResponse.class) {
                paramValues[i] = resp;
            } else if (parameterType == String.class) {
                for (Map.Entry<String, String[]> param : parameterMap.entrySet()) {
                    String value = Arrays.toString(param.getValue())
                            .replaceAll("\\[|\\]", "")
                            .replaceAll(",\\s", "");
                    paramValues[i] = value;
                }
            }
        }
        try {
            String beanName = lowerFirstCase(method.getDeclaringClass().getSimpleName());
            //利用反射机制来调用
            method.invoke(this.ioc.get(beanName),paramValues);
        } catch (Exception e) {
            e.printStackTrace();
        }



    }


    /**
     * 当Servlet容器启动时，会调用GPDispatcherServlet的init()方法，从init方法的参数中，我们可以拿到主配置文件的路径，
     * 从能够读取到配置文件中的信息。前面我们已经介绍了Spring的三个阶段，现在来完成初始化阶段的代码。
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        //1 、加载配置文件
        this.doLoadConfig(config.getInitParameter(LOCATION));
        //2、扫描所有相关的类
        this.doScanPackages(prop.getProperty("scanPackage"));
        //3、初始化所有相关类的实例，并保存到IOC容器中
        this.doInstance();
        //4、完成相关的依赖注入
        this.doAutoWired();
        //5、构造handlermapping
        this.doRecordMapping();
        System.out.println("bxlmvc framework is inited!");
    }

    //doRecordMapping()方法，将BxlRequestMapping中配置的信息和Method进行关联，并保存这些关系。
    private void doRecordMapping() {
        if (ioc.isEmpty()) return;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(BxlController.class)) continue;

            //先获取类上配置的路径
            String baseUrl = "";
            if (clazz.isAnnotationPresent(BxlRequestMapping.class)) {
                BxlRequestMapping bxlRequestMapping = clazz.getAnnotation(BxlRequestMapping.class);
                baseUrl = bxlRequestMapping.value();
            }

            //再获取方法上配置的路径
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                if (!method.isAnnotationPresent(BxlRequestMapping.class)) continue;
                //记录映射url
                BxlRequestMapping bxlRequestMapping = method.getAnnotation(BxlRequestMapping.class);
                String subUrl = bxlRequestMapping.value();
                String url = ("/" + baseUrl + "/" + subUrl).replaceAll("/+","/");
                handlerMapping.put(url,method);
                System.out.println("mapped:"+url+","+method.getName());
            }
        }
    }

    //doAutowired()方法，将初始化到IOC容器中的类，需要赋值的字段进行赋值
    private void doAutoWired() {
        if (ioc.isEmpty()) return;
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //拿到实例对象中的所有属性
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (!field.isAnnotationPresent(BxlAutowried.class)) continue;
                BxlAutowried bxlAutowried = field.getAnnotation(BxlAutowried.class);
                String beanName = bxlAutowried.value().trim();
                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (Exception e){
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    //doInstance()方法，初始化所有相关的类，并放入到IOC容器之中。IOC容器的key默认是类名首字母小写，
    // 如果是自己设置类名，则优先使用自定义的。因此，要先写一个针对类名首字母处理的工具方法。
    private void doInstance() {
        if (classNames.size()==0) return;
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(BxlController.class)){
                    //将clazz首字母小写作为beanName
                    String beanName = lowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName,clazz.newInstance());
                } else if (clazz.isAnnotationPresent(BxlService.class)) {
                    BxlService bxlService = clazz.getAnnotation(BxlService.class);
                    String beanName = bxlService.value();
                    //如果用户设置了名字，就使用自己设置的
                    if (!"".equals(beanName.trim())){
                        ioc.put(beanName,clazz.newInstance());
                        continue;
                    }
                    //如果自己没有设置，就按接口类型创建一个
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        ioc.put(i.getName(),clazz.newInstance());
                    }
                } else {
                    continue;
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private String lowerFirstCase(String str){
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    //doScanner()方法，递归扫描出所有的Class文件
    private void doScanPackages(String scanPackage) {
        //将所有的包路径转化为文件路径
        //URL resource = this.getClass().getClassLoader().getResource("com/bxl/demo");
        URL url = this.getClass().getClassLoader().getResource("" + scanPackage.replaceAll("\\.", "/"));
        File dir = new File(url.getFile());
        for (File file : dir.listFiles()) {
            //如果是文件夹，则递归调用
            if (file.isDirectory()){
                doScanPackages(scanPackage + "." + file.getName());
            } else {
                classNames.add(scanPackage + "." + file.getName().replace(".class","").trim());
            }
        }
    }

    //doLoadConfig()方法的实现，将文件读取到Properties对象中
    private void doLoadConfig(String location) {
        InputStream fis = null;
        try {
            fis = this.getClass().getClassLoader().getResourceAsStream(location);
            prop.load(fis);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis!=null){
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



}
