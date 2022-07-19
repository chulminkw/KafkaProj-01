package com.practice.kafka.consumer;


import com.practice.kafka.model.OrderModel;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class OrderSerdeConsumer {
    public static final Logger logger = LoggerFactory.getLogger(OrderSerdeConsumer.class.getName());

    private KafkaConsumer<String, OrderModel> kafkaConsumer;
    private List<java.lang.String> topics;

    public OrderSerdeConsumer(Properties consumerProps, List<java.lang.String> topics) {
        this.kafkaConsumer = new KafkaConsumer<String, OrderModel>(consumerProps);
        this.topics = topics;
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

    private void processRecord(ConsumerRecord<String, OrderModel> record) {
        logger.info("record key:{},  partition:{}, record offset:{} record value:{}",
                record.key(), record.partition(), record.offset(), record.value());
    }

    private void processRecords(ConsumerRecords<String, OrderModel> records) {
        records.forEach(record -> processRecord(record));
    }


    public void pollConsumes(long durationMillis, java.lang.String commitMode) {
        try {
            while (true) {
                if (commitMode.equals("sync")) {
                    pollCommitSync(durationMillis);
                } else {
                    pollCommitAsync(durationMillis);
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
            closeConsumer();
        }
    }

    private void pollCommitAsync(long durationMillis) throws WakeupException, Exception {
        ConsumerRecords<String, OrderModel> consumerRecords = this.kafkaConsumer.poll(Duration.ofMillis(durationMillis));
        processRecords(consumerRecords);
        this.kafkaConsumer.commitAsync( (offsets, exception) -> {
            if(exception != null) {
                logger.error("offsets {} is not completed, error:{}", offsets, exception.getMessage());
            }

        });

    }
   private void pollCommitSync(long durationMillis) throws WakeupException, Exception {
        ConsumerRecords<String, OrderModel> consumerRecords = this.kafkaConsumer.poll(Duration.ofMillis(durationMillis));
        processRecords(consumerRecords);
        try {
            if(consumerRecords.count() > 0 ) {
                this.kafkaConsumer.commitSync();
                logger.info("commit sync has been called");
            }
        } catch(CommitFailedException e) {
            logger.error(e.getMessage());
        }
    }
    public void closeConsumer() {
        this.kafkaConsumer.close();
    }

    public static void main(java.lang.String[] args) {
        java.lang.String topicName = "file-topic";

        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.56.101:9092");
        props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "file-group");
        props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        OrderSerdeConsumer orderConsumer = new OrderSerdeConsumer(props, List.of(topicName));
        orderConsumer.initConsumer();
        String commitMode = "async";

        orderConsumer.pollConsumes(100, commitMode);
        orderConsumer.closeConsumer();

    }


}