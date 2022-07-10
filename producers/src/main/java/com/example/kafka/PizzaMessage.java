package com.example.kafka;

import com.github.javafaker.Faker;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class PizzaMessage {
    private static final List<String> pizzaNames = List.of("Margherita", "Marinara",
            "Diavola", "Mari & Month", "Salami", "Peperoni");

    private static final List<String> pizzaToppings = List.of("tomato",
            "mozzarella", "blue cheese", "salami",
            "green peppers", "ham", "olives",
            "anchovies", "artichokes", "olives",
            "garlic", "tuna", "onion",
            "pineapple", "strawberry", "banana");

    private static final List<String> pizzaShop = List.of("Marios Pizza", "Luigis Pizza", "Circular Pi Pizzeria",
            "Ill Make You a Pizza You Can't Refuse", "Mammamia Pizza", "Its-a me! Mario Pizza!");

    public PizzaMessage() {}

    private String getRandomValue(List list) {
        int size = list.size();
        int index = (int)(Math.random() * size);

        return (String)list.get(index);

    }

    public HashMap<String, String> produce_msg(Faker faker, int orderNo) {
        String id = String.valueOf(orderNo);
        String shop = getRandomValue(pizzaShop);
        String pizzaName = getRandomValue(pizzaNames);

        String customerName = faker.name().fullName();
        String phoneNumber = faker.phoneNumber().phoneNumber();
        String address = faker.address().streetAddress();
        LocalDateTime now = LocalDateTime.now();
        String message = String.format("id:%s, shop:%s, pizza_name:%s, customer_name:%s, phone_number:%s, address:%s, time:%s"
                , id, shop, pizzaName, customerName, phoneNumber, address
                , now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss", Locale.ENGLISH)));;
        //System.out.println(message);
        HashMap<String, String> messageMap = new HashMap<>();
        messageMap.put("key", id);
        messageMap.put("message", message);

        return messageMap;
    }

    public static void main(String[] args) {
        PizzaMessage pizzaMessage = new PizzaMessage();
        Faker faker = Faker.instance();
        for(int i=0; i < 10; i++) {
            HashMap<String, String> message = pizzaMessage.produce_msg(faker, i);
            System.out.println("key:"+ message.get("key") + " message:" + message.get("message"));
        }
    }
}