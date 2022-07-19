package com.practice.kafka.producer;

import com.practice.kafka.model.OrderModel;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class OrderSerdeProducer {
    public static final Logger logger = LoggerFactory.getLogger(OrderSerdeProducer.class.getName());
    public static void main(String[] args) {

        String topicName = "order-serde-topic";

        //KafkaProducer configuration setting
        // null, "hello world"

        Properties props  = new Properties();

        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.56.101:9092");
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, OrderSerializer.class.getName());

        //KafkaProducer object creation
        KafkaProducer<String, OrderModel> kafkaProducer = new KafkaProducer<String, OrderModel>(props);
        String filePath = "C:\\Users\\q\\IdeaProjects\\my-app\\KafkaProj-01\\practice\\src\\main\\resources\\pizza_sample.txt";

        //KafkaProducer객체 생성->ProducerRecords생성 -> send() 비동기 방식 전송
        sendFileMessages(kafkaProducer, topicName, filePath);
        kafkaProducer.close();

    }

    private static void sendFileMessages(KafkaProducer<String, OrderModel> kafkaProducer, String topicName, String filePath) {
        String line = "";
        final String delimiter = ",";

        try {
            FileReader fileReader = new FileReader(filePath);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            while( (line = bufferedReader.readLine()) != null) {
                String[] tokens = line.split(delimiter);
                String key = tokens[0];
                StringBuffer value = new StringBuffer();

                OrderModel orderModel = new OrderModel(tokens[1], tokens[2], tokens[3],
                        tokens[4], tokens[5], tokens[6], LocalDateTime.parse(tokens[7].trim(), formatter));

                sendMessage(kafkaProducer, topicName, key, orderModel);

            }

        }catch(IOException e) {
            logger.info(e.getMessage());
        }
    }

    private static void sendMessage(KafkaProducer<String, OrderModel> kafkaProducer, String topicName, String key, OrderModel value) {
        ProducerRecord<String, OrderModel> producerRecord = new ProducerRecord<String, OrderModel>(topicName, key, value);
        logger.info("key:{}, value:{}", key, value);
        //kafkaProducer message send
        kafkaProducer.send(producerRecord, (metadata, exception) -> {
            if (exception == null) {
                logger.info("\n ###### record metadata received ##### \n" +
                        "partition:" + metadata.partition() + "\n" +
                        "offset:" + metadata.offset() + "\n" +
                        "timestamp:" + metadata.timestamp());
            } else {
                logger.error("exception error from broker " + exception.getMessage());
            }
        });

    }

}