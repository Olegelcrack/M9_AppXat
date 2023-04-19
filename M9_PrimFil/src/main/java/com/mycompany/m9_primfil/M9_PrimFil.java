/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
 */

package com.mycompany.m9_primfil;

/**
 *
 * @author DAM
 */
class NouFil extends Thread{
    private int cont;
    
    public NouFil(int s) {cont=s;}
    public void run(){
        System.out.println("Hola fil nou creat! contador:"+cont);
    }
}
public class M9_PrimFil{

    public static void main(String[] args) {
        int i;
        NouFil NF=new NouFil(0);
        for(i=1; i<6;i++)
            new NouFil(i).start();
        NF.start();
        System.out.println("Hola fil principal!");
        System.out.println("Fil principal acaba.");
    }
}
