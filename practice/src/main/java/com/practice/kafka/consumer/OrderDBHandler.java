package com.practice.kafka.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDBHandler {
    public static final Logger logger = LoggerFactory.getLogger(OrderDBHandler.class.getName());
    private Connection connection = null;
    private PreparedStatement insertPrepared = null;
    private static final String INSERT_ORDER_SQL = "INSERT INTO public.orders " +
            "(ord_id, shop_id, menu_name, user_name, phone_number, address, order_time) "+
            "values (?, ?, ?, ?, ?, ?, ?)";

    public OrderDBHandler(String url, String user, String password) {
        try {
            this.connection = DriverManager.getConnection(url, user, password);
            this.insertPrepared = this.connection.prepareStatement(INSERT_ORDER_SQL);
        } catch(SQLException e) {
            logger.error(e.getMessage());
        }

    }

    public void insertOrder(OrderDTO orderDTO)  {
        try {
            PreparedStatement pstmt = this.connection.prepareStatement(INSERT_ORDER_SQL);
            pstmt.setString(1, orderDTO.orderId);
            pstmt.setString(2, orderDTO.shopId);
            pstmt.setString(3, orderDTO.menuName);
            pstmt.setString(4, orderDTO.userName);
            pstmt.setString(5, orderDTO.phoneNumber);
            pstmt.setString(6, orderDTO.address);
            pstmt.setTimestamp(7, Timestamp.valueOf(orderDTO.orderTime));

            pstmt.executeUpdate();
        } catch(SQLException e) {
            logger.error(e.getMessage());
        }

    }

    public void insertOrders(List<OrderDTO> orders) {
       try {
            PreparedStatement pstmt = this.connection.prepareStatement(INSERT_ORDER_SQL);
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

    public void close()
    {
        try {
            logger.info("###### OrderDBHandler is closing");
            this.insertPrepared.close();
            this.connection.close();
        }catch(SQLException e) {
            logger.error(e.getMessage());
        }
    }

    public static void main(String[] args) {
        String url = "jdbc:postgresql://192.168.56.101:5432/postgres";
        String user = "postgres";
        String password = "postgres";
        OrderDBHandler orderDBHandler = new OrderDBHandler(url, user, password);

        LocalDateTime now = LocalDateTime.now();
        OrderDTO orderDTO = new OrderDTO("ord001", "test_shop", "test_menu",
                "test_user", "test_phone", "test_address",
                now);

        orderDBHandler.insertOrder(orderDTO);
        orderDBHandler.close();
    }


}
