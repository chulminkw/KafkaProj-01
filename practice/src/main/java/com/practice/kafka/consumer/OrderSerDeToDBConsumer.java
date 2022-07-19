package com.practice.kafka.consumer;

import com.practice.kafka.model.OrderModel;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class OrderSerDeToDBConsumer {
    public static final Logger logger = LoggerFactory.getLogger(OrderSerDeToDBConsumer.class.getName());
    protected KafkaConsumer<String, OrderModel> kafkaConsumer;
    protected List<String> topics;

    private OrderSerdeDBHandler orderDBHandler;
    public OrderSerDeToDBConsumer(Properties consumerProps, List<String> topics,
                                  OrderSerdeDBHandler orderDBHandler) {
        this.kafkaConsumer = new KafkaConsumer<String, OrderModel>(consumerProps);
        this.topics = topics;
        this.orderDBHandler = orderDBHandler;
    }
    public void initConsumer() {
        this.kafkaConsumer.subscribe(this.topics);
        shutdownHookToRuntime(this.kafkaConsumer);
    }

    private void shutdownHookToRuntime(KafkaConsumer<String, OrderModel> kafkaConsumer) {
        //main thread
        Thread mainThread = Thread.currentThread();

        //main thread 종료시 별도의 thread로 KafkaConsumer wakeup()메소드를 호출하게 함.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                logger.info(" main program starts to exit by calling wakeup");
                kafkaConsumer.wakeup();

                try {
                    mainThread.join();
                } catch(InterruptedException e) { e.printStackTrace();}
            }
        });

    }

    private void processRecord(ConsumerRecord<String, OrderModel> record) throws Exception {
        OrderModel orderModel = makeOrderDTO(record);
        orderDBHandler.insertOrder(orderModel);
    }

    private OrderModel makeOrderDTO(ConsumerRecord<String, OrderModel> record) {
        return record.value();
    }


    private void processRecords(ConsumerRecords<String, OrderModel> records) throws Exception{
        List<OrderModel> orders = makeOrders(records);
        orderDBHandler.insertOrders(orders);
    }

    private List<OrderModel> makeOrders(ConsumerRecords<String, OrderModel> records) throws Exception {
        List<OrderModel> orders = new ArrayList<>();
        //records.forEach(record -> orders.add(makeOrderDTO(record)));
        for(ConsumerRecord<String, OrderModel> record : records) {
            OrderModel orderModel = makeOrderDTO(record);
            orders.add(orderModel);
        }
        return orders;
    }


    public void pollConsumes(long durationMillis, String commitMode) {
        if (commitMode.equals("sync")) {
            pollCommitSync(durationMillis);
        } else {
            pollCommitAsync(durationMillis);
        }
    }
    private void pollCommitAsync(long durationMillis) {
        try {
            while (true) {
                ConsumerRecords<String, OrderModel> consumerRecords = this.kafkaConsumer.poll(Duration.ofMillis(durationMillis));
                logger.info("consumerRecords count:" + consumerRecords.count());
                if(consumerRecords.count() > 0) {
                    try {
                        processRecords(consumerRecords);
                    } catch(Exception e) {
                        logger.error(e.getMessage());
                    }
                }
//                if(consumerRecords.count() > 0) {
//                    for (ConsumerRecord<K, V> consumerRecord : consumerRecords) {
//                        processRecord(consumerRecord);
//                    }
//                }
                //commitAsync의 OffsetCommitCallback을 lambda 형식으로 변경
                this.kafkaConsumer.commitAsync((offsets, exception) -> {
                    if (exception != null) {
                        logger.error("offsets {} is not completed, error:{}", offsets, exception.getMessage());
                    }
                });
            }
        }catch(WakeupException e) {
            logger.error("wakeup exception has been called");
        }catch(Exception e) {
            logger.error(e.getMessage());
        }finally {
            logger.info("##### commit sync before closing");
            kafkaConsumer.commitSync();
            logger.info("finally consumer is closing");
            close();
        }
    }

    protected void pollCommitSync(long durationMillis) {
        try {
            while (true) {
                ConsumerRecords<String, OrderModel> consumerRecords = this.kafkaConsumer.poll(Duration.ofMillis(durationMillis));
                processRecords(consumerRecords);
                try {
                    if (consumerRecords.count() > 0) {
                        this.kafkaConsumer.commitSync();
                        logger.info("commit sync has been called");
                    }
                } catch (CommitFailedException e) {
                    logger.error(e.getMessage());
                }
            }
        }catch(WakeupException e) {
            logger.error("wakeup exception has been called");
        }catch(Exception e) {
            logger.error(e.getMessage());
        }finally {
            logger.info("##### commit sync before closing");
            kafkaConsumer.commitSync();
            logger.info("finally consumer is closing");
            close();
        }
    }
    protected void close() {
        this.kafkaConsumer.close();
        this.orderDBHandler.close();
    }

    public static void main(String[] args) {
        String topicName = "file-topic";

        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.56.101:9092");
        props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, OrderDeserializer.class.getName());
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "file-group-new");
        props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        String url = "jdbc:postgresql://192.168.56.101:5432/postgres";
        String user = "postgres";
        String password = "postgres";
        OrderSerdeDBHandler orderDBHandler = new OrderSerdeDBHandler(url, user, password);

        OrderSerDeToDBConsumer orderSerDeToDBConsumer = new
                OrderSerDeToDBConsumer(props, List.of(topicName), orderDBHandler);
        orderSerDeToDBConsumer.initConsumer();
        String commitMode = "async";

        orderSerDeToDBConsumer.pollConsumes(1000, commitMode);

    }

}