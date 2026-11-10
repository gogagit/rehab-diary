package ru.coursework.rehab.service;
public class BusinessException extends RuntimeException {
    public final int status;
    public BusinessException(int status,String message) { super(message); this.status=status; }
    public static BusinessException invalid(String message) { return new BusinessException(400,message); }
    public static BusinessException missing() { return new BusinessException(404,"Запись не найдена"); }
    public static BusinessException conflict(String message) { return new BusinessException(409,message); }
}
