package com.zzm.rocketmq.config;

import com.alibaba.rocketmq.client.consumer.DefaultMQPushConsumer;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import com.alibaba.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.client.producer.DefaultMQProducer;
import com.alibaba.rocketmq.client.producer.TransactionMQProducer;
import com.alibaba.rocketmq.common.consumer.ConsumeFromWhere;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.zzm.rocketmq.event.RocketMqEvent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * @Author:钟志苗
 * @Date: 8:36 2017/5/13
 * @Description:
 */
@Configuration
@EnableConfigurationProperties(RocketMqProperties.class)
public class RocketMqConfiguration {

	@Autowired
	private RocketMqProperties rmqProperties;

	@Autowired
	private ApplicationEventPublisher publisher;

	/**
	 * 发送普通消息
	 */
	@Bean(name = "default")
	public DefaultMQProducer defaultMQProducer() throws MQClientException {
		DefaultMQProducer producer = new DefaultMQProducer(rmqProperties.getProducer());
		producer.setNamesrvAddr(rmqProperties.getNamesrvAddr());
		producer.setInstanceName(rmqProperties.getInstanceName());
		producer.setVipChannelEnabled(false);
		producer.start();
		System.out.println("DefaultMQProducer is Started.");
		return producer;
	}

	/**
	 * 发送事务消息
	 */
	@Bean(name = "trans")
	public TransactionMQProducer transactionMQProducer() throws MQClientException {
		TransactionMQProducer producer = new TransactionMQProducer("TransactionProducerGroupName");
		producer.setNamesrvAddr(rmqProperties.getNamesrvAddr());
		producer.setInstanceName(rmqProperties.getInstanceName());
		// 事务回查最小并发数
		producer.setCheckThreadPoolMinSize(2);
		// 事务回查最大并发数
		producer.setCheckThreadPoolMaxSize(2);
		// 队列数
		producer.setCheckRequestHoldMax(2000);
		producer.start();
		System.out.println("TransactionMQProducer is Started.");
		return producer;
	}

	/**
	 * 消费者
	 */
	@Bean
	public DefaultMQPushConsumer pushConsumer() throws MQClientException {
		DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(rmqProperties.getConsumer());
		Set<String> setTopic = rmqProperties.getTopic();
		for (String topic : setTopic) {
			consumer.subscribe(topic, "*");
		}
		consumer.setNamesrvAddr(rmqProperties.getNamesrvAddr());
		consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
		consumer.setConsumeMessageBatchMaxSize(1);
		consumer.registerMessageListener(new MessageListenerConcurrently() {
			public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
				MessageExt msg = msgs.get(0);
				try {
					publisher.publishEvent(new RocketMqEvent(msg, consumer));
				} catch (Exception e) {
					if (msg.getReconsumeTimes() <= 1) {
						return ConsumeConcurrentlyStatus.RECONSUME_LATER;
					} else {
						System.out.println("定时重试！");
					}
				}
				return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
			}
		});
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(3000);
					try {
						consumer.start();
					} catch (MQClientException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

		}).start();
		return consumer;
	}

}