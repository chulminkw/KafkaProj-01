package com.practice.kafka.event;

import com.practice.kafka.model.OrderModel;
import com.practice.kafka.producer.OrderSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

//모니터링하는 file에 내용이 추가되었을 때 메시지를 producer를 이용하여 전송하는 클래스
//생성인자로 KafkaProcuer, 토픽명, SYNC 전송 여부를 전달 받음
//EventHandler interface를 구현. onMessage() 구현.
public class OrderSerdeEventHandler implements EventHandler {
    public static final Logger logger = LoggerFactory.getLogger(OrderSerdeEventHandler.class.getName());
    private KafkaProducer<String, OrderModel> kafkaProducer;
    private String topicName;
    private boolean sync;

    //KafkaProducer, 토픽명, SYNC 전송여부는 생성시 입력됨.
    public OrderSerdeEventHandler(KafkaProducer<String, OrderModel> kafkaProducer, String topicName, boolean sync) {
        this.kafkaProducer = kafkaProducer;
        this.topicName = topicName;
        this.sync = sync;
    }

    //파일에 내용이 Append되었을 때 호출됨. 추가된 라인 별로 MessageEvent를 생성하고 이를 Producer에서 전송.
    @Override
    public void onMessage(MessageEvent messageEvent) throws InterruptedException, ExecutionException {
        OrderModel order = getOrderFromMessage(messageEvent.value);
        ProducerRecord<String, OrderModel> producerRecord = new ProducerRecord<>(this.topicName, messageEvent.key, order);
        //logger.info("Order:" + order.toString());
        if(this.sync) {
            RecordMetadata recordMetadata = this.kafkaProducer.send(producerRecord).get();
//            logger.info("\n ###### record metadata received ##### \n" +
//                    "partition:" + recordMetadata.partition() +"\n" +
//                    "offset:" + recordMetadata.offset() + "\n" +
//                    "timestamp:" + recordMetadata.timestamp());
        } else {
            this.kafkaProducer.send(producerRecord, (metadata, exception) -> {
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

    private OrderModel getOrderFromMessage(String messageValue) {
        String[] tokens = messageValue.split(",");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        OrderModel order = new OrderModel(tokens[0], tokens[1], tokens[2], tokens[3],
                tokens[4], tokens[5], LocalDateTime.parse(tokens[6].trim(), formatter));

        return order;
    }

    //FileEventHandler가 제대로 생성되었는지 확인을 위해 직접 수행.
    public static void main(String[] args) throws Exception {
        String topicName = "file-topic";

        Properties props  = new Properties();
        props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.56.101:9092");
        props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, OrderSerializer.class.getName());

        KafkaProducer<String, OrderModel> kafkaProducer = new KafkaProducer<String, OrderModel>(props);
        boolean sync = true;

        OrderSerdeEventHandler orderSerdeEventHandler = new OrderSerdeEventHandler(kafkaProducer, topicName, sync);
        MessageEvent messageEvent = new MessageEvent("L001", "ord999, L001, Super Supreme, Grady Sawayn, 070-780-2916, 10564 Zelda Street, 2022-07-14 12:09:33");
        orderSerdeEventHandler.onMessage(messageEvent);
    }
}
