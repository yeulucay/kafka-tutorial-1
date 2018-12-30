package com.yeulucay.kafka.tutorial1;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class ConsumerDemoWithThread {

    private ConsumerDemoWithThread() { }

    public static void main(String[] args) {
        new ConsumerDemoWithThread().run();
    }

    private void run(){
        Logger logger = LoggerFactory.getLogger(ConsumerDemoWithThread.class.getName());

        String bootstrapServers = "127.0.0.1:9092";
        String groupId = "my-sixth-application";
        String topic = "first_topic";
        CountDownLatch latch = new CountDownLatch(1);

        // create the consumer runnable
        logger.info("Creating new consumer thread.");
        Runnable myConsumer = new ConsumerRunnable(topic, bootstrapServers, groupId, latch);
        // starting the thread
        Thread thread = new Thread(myConsumer);
        thread.start();

        // add a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            logger.info("Shotdown hook caught");
            ((ConsumerRunnable) myConsumer).shutdown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                logger.error("Application got interrupted.", e);
            }
            logger.info("Application has exited");
        }));

        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.error("Application got interrupted.", e);
        } finally{
            logger.info("Application is closing.");
        }
    }

    public class ConsumerRunnable implements Runnable{

        private CountDownLatch latch;
        private KafkaConsumer<String, String> consumer;
        private Logger logger = LoggerFactory.getLogger(ConsumerRunnable.class.getName());

        public ConsumerRunnable(String topic, String bootstrapServers, String groupId, CountDownLatch latch) {
            this.latch = latch;
            Properties properties = new Properties();
            properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // or latest or none
            this.consumer = new KafkaConsumer<String, String>(properties);
            consumer.subscribe(Arrays.asList(topic));
        }

        @Override
        public void run() {
            try {
                while (true) {

                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

                    for (ConsumerRecord record : records) {
                        logger.info("Key: " + record.key() + " | Value: " + record.value());
                        logger.info("Partition: " + record.partition() + " | Offset: " + record.offset());
                    }

                }
            } catch(WakeupException we){
                logger.info("Receiver shutdown signal!");
            } finally {
                consumer.close();
                latch.countDown();
            }
        }

        public void shutdown(){
            consumer.wakeup(); // interrupt consumer.poll(). It will throw WakeUpThrow exception

        }
    }
}
