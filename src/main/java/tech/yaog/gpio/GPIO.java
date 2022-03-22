package tech.yaog.gpio;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class GPIO implements Closeable {

    private int no;
    private String mode;

    private boolean isInited = false;

    public GPIO(int no, String mode) {
        this.no = no;
        this.mode = mode;
    }

    public boolean init() throws IOException {
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
        isInited = true;
        return true;
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
        GPIO gpio7 = new GPIO(7, "out");
        try {
            gpio7.init();
            System.in.read();

            gpio7.write(0);
            System.in.read();
            gpio7.write(1);
            System.in.read();
            gpio7.write(0);
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
        gpio7.release();
    }
}
