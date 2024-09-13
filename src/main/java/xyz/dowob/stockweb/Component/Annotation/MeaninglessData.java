package xyz.dowob.stockweb.Component.Annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 標記無意義的返回值方法
 * 如果標記在類上，則該類中的所有方法都被視為無意義的返回值方法
 *
 * @author yuan
 * @program Stock-Web
 * @ClassName MeaninglessData
 * @description
 * @create 2024-09-13 01:14
 * @Version 1.0
 **/
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface MeaninglessData {}
