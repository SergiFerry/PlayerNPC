package dev.sergiferry.spigot.metrics;

/**
 * Creado por SergiFerry el 11/09/2021
 */
public class MetricCount {

    private int count;

    public MetricCount(){
        this.count = 0;
    }

    public void addCount(){
        addCount(1);
    }

    public void addCount(int a){
        count += a;
    }

    public void setCount(int a){
        count = a;
    }

    public Integer getCount(){
        return count;
    }

    public Integer getFinalCount(){
        int cf = count;
        count = 0;
        return cf;
    }
}
