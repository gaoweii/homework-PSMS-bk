package system.module.core;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import system.annotation.Port;
import system.annotation.ProcessRun;
import org.apache.commons.collections.map.MultiValueMap;
import system.module.FrameworkMsg;
import system.module.processpool.PCB;
import system.module.processpool.ProcessPool;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.*;


/**
 * @author 高威
 * @version 1.1
 * 框架类，通过扫描process文件夹下的所有java文件,自动装配为PCB模块到主程序中运行,(只有添加了ProcessRun注解的类才能执行相应方法)
 *
 * */

public class ProcessFramework {

    private static Logger logger = LoggerFactory.getLogger("log");

    private List<String> allClassesPath;

    private List<String> allServicePath;

    private List<PCB> pcbList;

    private ArrayList<Module> modules;

    private static Map<String, Object> allBeans;

    private static FrameworkMsg frameworkMsg;

    private static  List<PCB> PCBList = new ArrayList<PCB>();

    private static MultiValueMap PCBProcessMethods = new MultiValueMap();

    private static Map<String, Module> allService = new HashMap<>();

    public static List<PCB> getPCBList() {
        return PCBList;
    }

    public static FrameworkMsg getFrameworkMsg() {
       return frameworkMsg;
    }

    /**这个Map里有所有线程的信息，用于LockSupport唤醒指定线程*/
    public static Map<String, Thread> getThreadList() {
        return threadList;
    }

    public static Map<String, String> getPCBProcessMethods() {
        return PCBProcessMethods;
    }

    private static Map<String, Thread> threadList;

    public static Map<String, Module> getAllService() {
        return allService;
    }

    public static Object getBean(String name) {
        return allBeans.get(name);
    }


    /**
     * 得到process文件夹下的所有java文件并转化为包的形式
     * */
    public List<String> getAllClasses(String path , String root) {
        List<String> pathes = new ArrayList<String>();
        File file = new File(path);
        File[] files = file.listFiles();
        if (files == null || files.length == 0) {
            return null;
        }
        /**对指定文件夹进行递归搜索，搜索出所有的java文件用于加载到框架里*/
        for (File f : files) {
            if (f.isDirectory()) {
               pathes.addAll(getAllClasses(f.getAbsolutePath(), root));
            } else if (f.isFile()) {
                String suffix = f.getName().substring(f.getName().lastIndexOf(".") + 1);
                String prefix = f.getPath().substring(0, f.getPath().lastIndexOf("."));
                if (suffix.equals("java")) {
                    String res = prefix.substring(f.getPath().indexOf(root)).replace("\\", ".");
                    pathes.add(res);
                }
            }
        }
        return pathes;

    }

    //初始化框架,将@ProccessRun注解过的类加载到链表里
    @SneakyThrows
    public void init() {

        logger.info("ProcessFramework init at " + new Date(System.currentTimeMillis()).toString());
        pcbList = new ArrayList<PCB>();
        allBeans = new HashMap<>();
        allClassesPath = getAllClasses("src\\main\\java\\process", "process");
        if(allClassesPath == null) {
            return;
        }

        for (String cpath : allClassesPath) {
            try {

                Class aClass = Class.forName(cpath);
                Method[] methods = aClass.getMethods();
                Field[] fields = aClass.getFields();
                for (Method  method : methods) {
                   ProcessRun processRun = method.getAnnotation(ProcessRun.class);
                   if (processRun != null) {
                       method.setAccessible(true);
                       logger.info("add " + aClass.getName() + " to framework...");
                       PCB pcb = new PCB();
                       /**这里注意，对于每个PCB模块，执行原类的默认构造函数，构造好后加载到PCB模块*/
                       pcb.setTask(aClass.getConstructor().newInstance());
                       pcb.setProcessInfo(aClass);
                       PCBProcessMethods.put(cpath, method.getName());
                       if (processRun.name().equals("")) {
                           pcb.setName("task-" + cpath + "-" + method.getName());
                       } else {
                           pcb.setName("task-" + cpath + "-" + processRun.name());
                       }
                       pcb.setPriority(processRun.priority());
                       pcb.setServiceTime(processRun.serviceTime());
                       pcb.setRequestTime(processRun.requestTime());
                       pcb.setMethod(method.getName());
                       pcbList.add(pcb);
                       PCBList.add(pcb);
                       allBeans.put(pcb.getName(), pcb);
                   }
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }


    }
/**
 * 初始化模块*/
    @SneakyThrows
    public void initModules() {
        modules = new ArrayList<>();
        /**这里注意，由于FrameworkMsg放在文件夹的最上层，所以扫描地址的时候最先扫描到，最后执行初始化操作的时候也最先执行*/
        allServicePath = getAllClasses("src\\main\\java\\system", "system");
        if (allServicePath ==null) {
            return;
        }

        for (String spath : allServicePath) {
            Class aClass = Class.forName(spath);
            Class[] interfaces = aClass.getInterfaces();
            Module module;
            for (Class aInterface : interfaces) {
                if (aInterface.getName().equals("system.module.core.Module")) {
                    logger.info("add " + aClass.getName() + " to framework...");
                    /**这里的Port注解是由于java的WebSocket必须在执行构造函数时就提供端口信息，否则就是默认的80端口，所以开发了Port注解方便自动配置端口*/
                    if (aClass.getSuperclass().getName().equals("org.java_websocket.server.WebSocketServer")) {
                        Port port = (Port) aClass.getAnnotation(Port.class);
                        if (port != null) {
                            module = (Module) aClass.getConstructor(InetSocketAddress.class).newInstance(new InetSocketAddress(port.value()));
                        } else {
                            module = (Module) aClass.getConstructor(InetSocketAddress.class).newInstance(new InetSocketAddress(80));
                        }

                    } else {
                        module = (Module) aClass.newInstance();
                    }
                    module.init();
                    if (aClass.equals(FrameworkMsg.class)) {
                        frameworkMsg = (FrameworkMsg) module;
                    }
                    allService.put(aClass.getName(), module);
                    modules.add(module);
                    allBeans.put(aClass.getName(), module);
                    break;
                }
            }
        }
    }

    public void close() {
        for (Module module : modules) {
            module.close();
        }
    }



    /** 框架的执行类
     * 多线程运行不同模块 */
    public  void run() {
        try {
            init();
            initModules();
            threadList = new HashMap<>();

            for (Module module : modules) {
                Thread thread = new Thread(module);
                /**设置为守护线程*/
                thread.setDaemon(true);
                thread.setName(module.getClass().getName());
                thread.setPriority(5);
                if (thread.getName().equals("system.module.processpool.ProcessPool")) {
                    thread.setPriority(7);
                }
                threadList.put(thread.getName(), thread);
                thread.start();
            }
        } catch (Exception e) {
            logger.error("", e);
        }

    }

}
