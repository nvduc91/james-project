/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.cassandra;

import static org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule.MODULE_WITH_QUOTA;
import static org.apache.james.mailbox.elasticsearch.IndexAttachments.YES;
import static org.apache.james.mailbox.elasticsearch.MailboxElasticSearchConstants.DEFAULT_MAILBOX_READ_ALIAS;
import static org.apache.james.mailbox.elasticsearch.MailboxElasticSearchConstants.DEFAULT_MAILBOX_WRITE_ALIAS;
import static org.apache.james.mailbox.elasticsearch.MailboxIndexCreationUtil.prepareDefaultClient;
import static org.apache.james.mailbox.events.Event.EventId.random;
import static org.apache.james.mailbox.store.event.EventFactory.added;
import static org.apache.james.mailbox.store.mail.MessageMapper.FetchType.Metadata;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.backends.es.DockerElasticSearchExtension;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.ReactorElasticSearchClient;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.elasticsearch.MailboxElasticSearchConstants;
import org.apache.james.mailbox.elasticsearch.MailboxIdRoutingKeyFactory;
import org.apache.james.mailbox.elasticsearch.MailboxIndexCreationUtil;
import org.apache.james.mailbox.elasticsearch.events.ElasticSearchListeningMessageSearchIndex;
import org.apache.james.mailbox.elasticsearch.json.MessageToElasticSearchJson;
import org.apache.james.mailbox.elasticsearch.query.CriterionConverter;
import org.apache.james.mailbox.elasticsearch.query.QueryConverter;
import org.apache.james.mailbox.elasticsearch.search.ElasticSearchSearcher;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.Event.EventId;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.PreDeletionHooks;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.driver.core.BoundStatement;
import com.google.common.collect.ImmutableSet;

public class ElasticSearchWithCassandraMailboxTest {
    private static final int BATCH_SIZE = 1;
    private static final int SEARCH_SIZE = 1;
    private static final Username BOB = Username.of("bob");
    private static final MailboxPath INBOX = MailboxPath.inbox(BOB);
    private static final ZoneId PARIS_ZONE = ZoneId.of("Europe/Paris");

    @RegisterExtension
    static CassandraClusterExtension cassandra = new CassandraClusterExtension(MODULE_WITH_QUOTA);
    @RegisterExtension
    static DockerElasticSearchExtension elasticSearch = new DockerElasticSearchExtension();

    private CassandraMailboxManager mailboxManager;
    private ElasticSearchListeningMessageSearchIndex searchIndex;

    @BeforeEach
    void setUp() throws Exception {
        mailboxManager = CassandraMailboxManagerProvider.provideMailboxManager(
            cassandra.getCassandraCluster(),
            new PreDeletionHooks(ImmutableSet.of(), new RecordingMetricFactory()));

        ReactorElasticSearchClient client = prepareDefaultClient(
            elasticSearch.getDockerElasticSearch().clientProvider().get(),
            elasticSearch.getDockerElasticSearch().configuration());

        MailboxIdRoutingKeyFactory routingKeyFactory = new MailboxIdRoutingKeyFactory();

        searchIndex = new ElasticSearchListeningMessageSearchIndex(
            mailboxManager.getMapperFactory(),
            new ElasticSearchIndexer(client,
                DEFAULT_MAILBOX_WRITE_ALIAS,
                BATCH_SIZE),
            new ElasticSearchSearcher(client, new QueryConverter(new CriterionConverter()), SEARCH_SIZE,
                new CassandraId.Factory(), new CassandraMessageId.Factory(),
                DEFAULT_MAILBOX_READ_ALIAS, routingKeyFactory),
            new MessageToElasticSearchJson(new DefaultTextExtractor(), PARIS_ZONE, YES),
            mailboxManager, routingKeyFactory);
    }

    @Test
    void test1(CassandraCluster cassandra) throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        mailboxManager.createMailbox(INBOX, session);
        MessageManager messageManager = mailboxManager.getMailbox(INBOX, session);
        MessageManager.AppendResult appendResult = messageManager
            .appendMessage(MessageManager.AppendCommand.builder()
                .build("header: value\r\n\r\n"), session);

        Mailbox mailbox = messageManager.getMailboxEntity();
        MessageMetaData metaData = mailboxManager.getMapperFactory().getMessageMapper(session)
            .findInMailbox(mailbox, MessageRange.one(appendResult.getId().getUid()), Metadata, 1)
            .next()
            .metaData();
        MailboxListener.Added event = added()
            .eventId(random())
            .mailboxSession(session)
            .mailbox(mailbox)
            .addMetaData(metaData)
            .build();

        cassandra.getConf().printStatements();
        StatementRecorder statementRecorder = new StatementRecorder();
        cassandra.getConf().recordStatements(statementRecorder);
        searchIndex.event(event);

        assertThat(statementRecorder.listExecutedStatements())
            .filteredOn(statement -> statement instanceof BoundStatement)
            .extracting(BoundStatement.class::cast)
            .extracting(statement -> statement.preparedStatement().getQueryString())
            .filteredOn(statementString -> statementString.equals("SELECT acl,version FROM acl WHERE id=:id;"))
            .hasSize(1);
    }
}
