package com.practice.kafka.event;

public class MessageEvent {
    public String key;
    public String value;

    public MessageEvent(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
