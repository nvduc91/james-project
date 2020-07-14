/**
 * *************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.task.eventsourcing.distributed;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.task.eventsourcing.distributed.RabbitMQTerminationSubscriber.EXCHANGE_NAME;
import static org.apache.james.task.eventsourcing.distributed.RabbitMQTerminationSubscriber.ROUTING_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.ONE_MINUTE;
import static org.awaitility.Duration.TEN_SECONDS;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.JsonEventSerializer;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.json.DTOConverter;
import org.apache.james.server.task.json.JsonTaskSerializer;
import org.apache.james.task.eventsourcing.TerminationSubscriber;
import org.apache.james.task.eventsourcing.TerminationSubscriberContract;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;

class RabbitMQTerminationSubscriberTest implements TerminationSubscriberContract {
    private static final JsonTaskSerializer TASK_SERIALIZER = JsonTaskSerializer.of();
    private static final Set<EventDTOModule<? extends Event, ? extends EventDTO>> MODULES = TasksSerializationModule.list(TASK_SERIALIZER, DTOConverter.of(), DTOConverter.of());
    private static final JsonEventSerializer SERIALIZER = JsonEventSerializer.forModules(MODULES).withoutNestedType();

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ();

    @Override
    public TerminationSubscriber subscriber() {
        RabbitMQTerminationSubscriber subscriber = new RabbitMQTerminationSubscriber(rabbitMQExtension.getSender(),
            rabbitMQExtension.getReceiverProvider(), SERIALIZER);
        subscriber.start();
        return subscriber;
    }

    @Test
    void givenTwoTerminationSubscribersWhenAnEventIsSentItShouldBeReceivedByBoth() {
        TerminationSubscriber subscriber1 = subscriber();
        TerminationSubscriber subscriber2 = subscriber();

        Flux<Event> firstListener = Flux.from(subscriber1.listenEvents());
        Flux<Event> secondListener = Flux.from(subscriber2.listenEvents());

        sendEvents(subscriber1, COMPLETED_EVENT);

        List<Event> receivedEventsFirst = new ArrayList<>();
        firstListener.subscribe(receivedEventsFirst::add);
        List<Event> receivedEventsSecond = new ArrayList<>();
        secondListener.subscribe(receivedEventsSecond::add);

        Awaitility.await().atMost(ONE_MINUTE).until(() -> receivedEventsFirst.size() == 1 && receivedEventsSecond.size() == 1);

        assertThat(receivedEventsFirst).containsExactly(COMPLETED_EVENT);
        assertThat(receivedEventsSecond).containsExactly(COMPLETED_EVENT);
    }

    @Test
    void eventProcessingShouldNotCrashOnInvalidMessage() {
        Flux<Event> firstListener = Flux.from(subscriber().listenEvents());

        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(EXCHANGE_NAME,
                ROUTING_KEY,
                "BAD_PAYLOAD!".getBytes(UTF_8))))
            .block();

        List<Event> receivedEventsFirst = new ArrayList<>();
        firstListener.subscribe(receivedEventsFirst::add);

        await().timeout(TEN_SECONDS).untilAsserted(() -> assertThat(receivedEventsFirst).isEmpty());
    }

    @Test
    void eventProcessingShouldNotCrashOnInvalidMessages() {
        TerminationSubscriber subscriber1 = subscriber();
        TerminationSubscriber subscriber2 = subscriber();
        Flux<Event> firstListener = Flux.from(subscriber1.listenEvents());
        Flux<Event> secondListener = Flux.from(subscriber2.listenEvents());

        IntStream.range(0, 10).forEach(i -> rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(EXCHANGE_NAME,
                ROUTING_KEY,
                "BAD_PAYLOAD!".getBytes(StandardCharsets.UTF_8))))
            .block());

        sendEvents(subscriber1, COMPLETED_EVENT);

        List<Event> receivedEventsFirst = new ArrayList<>();
        firstListener.subscribe(receivedEventsFirst::add);
        List<Event> receivedEventsSecond = new ArrayList<>();
        secondListener.subscribe(receivedEventsSecond::add);

        await().atMost(ONE_MINUTE).untilAsserted(() ->
            SoftAssertions.assertSoftly(soft -> {
                assertThat(receivedEventsFirst).containsExactly(COMPLETED_EVENT);
                assertThat(receivedEventsSecond).containsExactly(COMPLETED_EVENT);
            }));
    }
}