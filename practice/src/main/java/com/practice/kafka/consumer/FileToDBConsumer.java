package com.practice.kafka.consumer;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class FileToDBConsumer<K extends Serializable, V extends Serializable> {
    public static final Logger logger = LoggerFactory.getLogger(FileToDBConsumer.class.getName());
    protected KafkaConsumer<K, V> kafkaConsumer;
    protected List<String> topics;

    private Connection connection;

    public FileToDBConsumer(Properties consumerProps, List<String> topics, Properties dbProps) {
        this.kafkaConsumer = new KafkaConsumer<K, V>(consumerProps);
        this.topics = topics;
        connection = getDBConnection(dbProps.getProperty("url"),
                dbProps.getProperty("user"), dbProps.getProperty("password"));
    }
    public void initConsumer() {
        this.kafkaConsumer.subscribe(this.topics);
        shutdownHookToRuntime(this.kafkaConsumer);
    }

    private void shutdownHookToRuntime(KafkaConsumer<K, V> kafkaConsumer) {
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

    private Connection getDBConnection(String url, String user, String password) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, user, password);
        } catch(SQLException e) {
            logger.error(e.getMessage());
        }
        return conn;
    }

    private void processRecord(ConsumerRecord<K, V> record) {
        OrderDTO orderDTO = makeOrderDTO(record);
        insertOrder(orderDTO);
    }

    private OrderDTO makeOrderDTO(ConsumerRecord<K,V> record) {
        String messageValue = (String)record.value();
        String[] tokens = messageValue.split(",");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        OrderDTO orderDTO = new OrderDTO(tokens[1], tokens[2], tokens[3], tokens[4],
                tokens[5], tokens[6], LocalDateTime.parse(tokens[7], formatter));

        return orderDTO;
    }

    private void insertOrder(OrderDTO orderDTO)  {
        String INSERT_SQL = "INSERT INTO orders " +
                "(ord_id, shop_id, menu_name, user_name, phone_number, address, order_time) "+
                "(?, ?, ?, ?, ?, ?, ?)";
        try {
            PreparedStatement pstmt = this.connection.prepareStatement(INSERT_SQL);
            pstmt.setString(1, orderDTO.orderId);
            pstmt.setString(2, orderDTO.shopId);
            pstmt.setString(3, orderDTO.menuName);
            pstmt.setString(4, orderDTO.userName);
            pstmt.setString(5, orderDTO.phoneNumber);
            pstmt.setString(6, orderDTO.address);
            pstmt.setTimestamp(7, Timestamp.valueOf(orderDTO.orderTime));
            logger.info("insert sql:" + INSERT_SQL);

            pstmt.executeUpdate();
        } catch(SQLException e) {
            logger.error(e.getMessage());
        }

    }

    private void processRecords(ConsumerRecords<K, V> records) {
        List<OrderDTO> orders = makeOrders(records);
        insertOrders(orders);
    }

    private List<OrderDTO> makeOrders(ConsumerRecords<K,V> records) {
        List<OrderDTO> orders = new ArrayList<>();
        records.forEach(record -> orders.add(makeOrderDTO(record)));
//        for(ConsumerRecord<K, V> record : records) {
//            orders.add(makeOrderDTO(record));
//        }
        return orders;
    }

    private void insertOrders(List<OrderDTO> orders) {
        String INSERT_SQL = "INSERT INTO orders " +
                "(ord_id, shop_id, menu_name, user_name, phone_number, address, order_time) "+
                "(?, ?, ?, ?, ?, ?, ?)";
        try {
            PreparedStatement pstmt = this.connection.prepareStatement(INSERT_SQL);
            for(OrderDTO orderDTO : orders) {
                pstmt.setString(1, orderDTO.orderId);
                pstmt.setString(2, orderDTO.shopId);
                pstmt.setString(3, orderDTO.menuName);
                pstmt.setString(4, orderDTO.userName);
                pstmt.setString(5, orderDTO.phoneNumber);
                pstmt.setString(6, orderDTO.address);
                pstmt.setTimestamp(7, Timestamp.valueOf(orderDTO.orderTime));

                pstmt.addBatch();
            }
            pstmt.executeUpdate();

        } catch(SQLException e) {
            logger.info(e.getMessage());
        }

    }

    public void pollConsumes(long durationMillis, String commitMode) {
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
    protected void pollCommitAsync(long durationMillis) throws WakeupException, Exception {
        ConsumerRecords<K, V> consumerRecords = this.kafkaConsumer.poll(Duration.ofMillis(durationMillis));
        processRecords(consumerRecords);
        this.kafkaConsumer.commitAsync(new OffsetCommitCallback() {
            @Override
            public void onComplete(Map<TopicPartition, OffsetAndMetadata> offsets, Exception exception) {
                if(exception != null) {
                    logger.error("offsets {} is not completed, error:{}", offsets, exception.getMessage());
                }
            }
        });
    }

    protected void pollCommitSync(long durationMillis) throws WakeupException, Exception {
        ConsumerRecords<K, V> consumerRecords = this.kafkaConsumer.poll(Duration.ofMillis(durationMillis));
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
    protected void closeConsumer() {
        this.kafkaConsumer.close();
    }

    public static void main(String[] args) {
        String topicName = "file-topic";

        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.56.101:9092");
        props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "group_03");
        props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        Properties dbProps = new Properties();
        dbProps.setProperty("url", "jdbc:postgresql://localhost/postgres");
        dbProps.setProperty("user", "postgres");
        dbProps.setProperty("password", "postgres");

        FileToDBConsumer<String, String> baseConsumer = new FileToDBConsumer<String, String>(props, List.of(topicName), dbProps);
        baseConsumer.initConsumer();
        String commitMode = "async";

        baseConsumer.pollConsumes(100, commitMode);

    }

}
