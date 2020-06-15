package system.module.core;


/**Module扩展了Runnable接口，因此每个Module均可以加载到一个线程*/
public interface Module extends Runnable {

    void init();

    void run();

    void close();
}
