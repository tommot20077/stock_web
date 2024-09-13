package xyz.dowob.stockweb.Component.Annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感數據的切面方法: 用於對敏感數據進行加密處理
 *
 * @author yuan
 * @program Stock-Web
 * @ClassName SensitiveData
 * @description
 * @create 2024-09-13 01:05
 * @Version 1.0
 **/
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SensitiveData {}
