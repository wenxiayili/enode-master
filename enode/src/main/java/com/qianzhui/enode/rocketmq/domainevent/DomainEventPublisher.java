package com.qianzhui.enode.rocketmq.domainevent;

import com.alibaba.rocketmq.common.message.Message;
import com.qianzhui.enode.common.container.GenericTypeLiteral;
import com.qianzhui.enode.common.container.ObjectContainer;
import com.qianzhui.enode.common.io.AsyncTaskResult;
import com.qianzhui.enode.common.logging.ILogger;
import com.qianzhui.enode.common.logging.ILoggerFactory;
import com.qianzhui.enode.common.serializing.IJsonSerializer;
import com.qianzhui.enode.common.utilities.BitConverter;
import com.qianzhui.enode.common.utilities.Ensure;
import com.qianzhui.enode.eventing.DomainEventStreamMessage;
import com.qianzhui.enode.eventing.IDomainEvent;
import com.qianzhui.enode.eventing.IEventSerializer;
import com.qianzhui.enode.infrastructure.IMessagePublisher;
import com.qianzhui.enode.rocketmq.ITopicProvider;
import com.qianzhui.enode.rocketmq.RocketMQMessageTypeCode;
import com.qianzhui.enode.rocketmq.SendQueueMessageService;
import com.qianzhui.enode.rocketmq.TopicTagData;
import com.qianzhui.enode.rocketmq.client.Producer;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

/**
 * Created by junbo_xu on 2016/4/5.
 */
public class DomainEventPublisher implements IMessagePublisher<DomainEventStreamMessage> {
    private final IJsonSerializer _jsonSerializer;
    private final ITopicProvider<IDomainEvent> _eventTopicProvider;
    private final IEventSerializer _eventSerializer;
    private final Producer _producer;
    private final SendQueueMessageService _sendMessageService;
    private final ILogger _logger;

    public Producer getProducer() {
        return _producer;
    }

    @Inject
    public DomainEventPublisher(Producer producer) {
        _producer = producer;
        _jsonSerializer = ObjectContainer.resolve(IJsonSerializer.class);
        _eventTopicProvider = ObjectContainer.resolve(new GenericTypeLiteral<ITopicProvider<IDomainEvent>>() {
        });
        _eventSerializer = ObjectContainer.resolve(IEventSerializer.class);
        _sendMessageService = new SendQueueMessageService();
        _logger = ObjectContainer.resolve(ILoggerFactory.class).create(getClass());
    }

    public DomainEventPublisher start() {
        return this;
    }

    public DomainEventPublisher shutdown() {
        return this;
    }

    public CompletableFuture<AsyncTaskResult> publishAsync(DomainEventStreamMessage eventStream) {
        Message message = createRocketMQMessage(eventStream);
        return _sendMessageService.sendMessageAsync(_producer, message, eventStream.getRoutingKey() == null ? eventStream.aggregateRootId() : eventStream.getRoutingKey());
    }

    private Message createRocketMQMessage(DomainEventStreamMessage eventStream) {
        Ensure.notNull(eventStream.aggregateRootId(), "aggregateRootId");
        EventStreamMessage eventMessage = createEventMessage(eventStream);
        TopicTagData topicTagData = _eventTopicProvider.getPublishTopic(null);
        String data = _jsonSerializer.serialize(eventMessage);

        byte[] body = BitConverter.getBytes(data);

        //TODO eventstream tags
        //TODO rocketmq message key of eventstream,default:eventStream.id()
        return new Message(topicTagData.getTopic(),
//                eventStream.getTag(),
                topicTagData.getTag(),
                eventStream.id(),
                RocketMQMessageTypeCode.DomainEventStreamMessage.getValue(), body, true);
    }

    private EventStreamMessage createEventMessage(DomainEventStreamMessage eventStream) {
        EventStreamMessage message = new EventStreamMessage();

        message.setCommandId(eventStream.getCommandId());
        message.setAggregateRootTypeName(eventStream.aggregateRootTypeName());
        message.setAggregateRootId(eventStream.aggregateRootId());
        message.setTimestamp(eventStream.timestamp());
        message.setVersion(eventStream.version());
        message.setEvents(_eventSerializer.serialize(eventStream.getEvents()));
        message.setItems(eventStream.getItems());

        return message;
    }
}
