package tech.yaog.gpio;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class GPIO implements Closeable {

    private int no;
    private String mode;

    private Thread watchThread;

    private boolean isInited = false;

    public GPIO(int no, String mode) {
        this.no = no;
        this.mode = mode;
    }

    public boolean init() throws IOException {
        return init(false);
    }

    public boolean init(boolean watchEdge) throws IOException {
        if (isInited) {
            release();
        }
        try (FileOutputStream fos = new FileOutputStream("/sys/class/gpio/export")){
            String write = no+"";
            fos.write(write.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        try (FileOutputStream fos = new FileOutputStream("/sys/class/gpio/gpio"+no+"/direction")){
            fos.write(mode.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        if (watchEdge) {
            try (FileOutputStream fos = new FileOutputStream("/sys/class/gpio/gpio"+no+"/edge")) {
                fos.write("both".getBytes(StandardCharsets.UTF_8));
                fos.flush();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return false;
            }
        }
        isInited = true;
        return true;
    }

    public interface EdgeEvent {
        void onChanged();
    }

    public boolean watch(EdgeEvent edgeEvent) {
        Path path = Paths.get("/sys/class/gpio/gpio"+no);
        WatchService watcher = null;
        try {
            watcher = FileSystems.getDefault().newWatchService();
            // 注册指定目录使用的监听器，监视目录下文件的变化；
            // PS：Path必须是目录，不能是文件；
            // StandardWatchEventKinds.ENTRY_MODIFY，表示监视文件的修改事件
            path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
            WatchService finalWatcher = watcher;
            watchThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        // 获取目录的变化:
                        // take()是一个阻塞方法，会等待监视器发出的信号才返回。
                        // 还可以使用watcher.poll()方法，非阻塞方法，会立即返回当时监视器中是否有信号。
                        // 返回结果WatchKey，是一个单例对象，与前面的register方法返回的实例是同一个；
                        WatchKey key = null;
                        try {
                            key = finalWatcher.take();
                            // 处理文件变化事件：
                            // key.pollEvents()用于获取文件变化事件，只能获取一次，不能重复获取，类似队列的形式。
                            for (WatchEvent<?> event : key.pollEvents()) {
                                // event.kind()：事件类型
                                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                                    //事件可能lost or discarded
                                    continue;
                                }
                                // 返回触发事件的文件或目录的路径（相对路径）
                                Path fileName = (Path) event.context();
                                if (fileName.toFile().getName().equals("value")) {
                                    if (edgeEvent != null) {
                                        edgeEvent.onChanged();
                                    }
                                }
                            }
                            // 每次调用WatchService的take()或poll()方法时需要通过本方法重置
                            if (!key.reset()) {
                                break;
                            }
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    try {
                        finalWatcher.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            watchThread.setPriority(Thread.MAX_PRIORITY);
            watchThread.setName("GPIO"+no+"Watcher");
            watchThread.start();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public int read() throws IOException {
        try (FileInputStream fis = new FileInputStream("/sys/class/gpio/gpio"+no+"/value")){
            byte[] data = new byte[]{0};
            fis.read(data, 0, 1);
            if (data[0] == '1') {
                return 1;
            }
            else {
                return 0;
            }
        }
    }

    public void write(int value) throws IOException {
        try (FileOutputStream fos = new FileOutputStream("/sys/class/gpio/gpio"+no+"/value")){
            byte[] data = (value+"").getBytes(StandardCharsets.UTF_8);
            fos.write(data, 0, 1);
        }
    }

    public void release() {
        if (isInited) {
            try (FileOutputStream fos = new FileOutputStream("/sys/class/gpio/unexport")){
                String write = no+"";
                fos.write(write.getBytes(StandardCharsets.UTF_8));
                fos.flush();
                isInited = false;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void close() throws IOException {
        release();
    }

    public static void main(String[] args) {
        GPIO gpio7 = new GPIO(27, "in");
        try {
            gpio7.init(true);
            System.out.println("GPIO Inited");
            int val = gpio7.read();
            System.out.println("Value: "+val);
            gpio7.watch(() -> {
                try {
                    int valn = gpio7.read();
                    System.out.println("New Value: "+valn);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
        gpio7.release();
    }
}
