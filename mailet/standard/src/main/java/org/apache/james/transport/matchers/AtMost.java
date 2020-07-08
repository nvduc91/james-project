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

package org.apache.james.transport.matchers;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.mail.MessagingException;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.MailAddress;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;
import org.apache.mailet.base.MailetUtil;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
<<<<<<< 2ac2f1c25f5911ee496ecf17224c763445337601
 * Checks that a mail did at most X executions on a specific operation.
 *
 * If no executions have been performed previously, it sets up an attribute `AT_MOST_EXECUTIONS`
 * in the mail that will be incremented every time the check succeeds.
=======
 * <p>Checks that a mail did at most X retries on a specific operation</p>
 *
 * <p> If no retries have been performed previously for Y attribute, it'll be sets up.</p>
 * <p> In the mail, every time the check was succeeds, counter of it will be increment by one.
 * The check fails when the defined X limit is reached.</p>
>>>>>>> j3295: handle configuration AtMostMatcher with differents config
 *
 * <ul>
 * <li>X - count of how many times a specific operation retried</li>
 * <li>Y - name of attribute represented for specific operation retried, default value is: <i>AT_MOST_TRIES</i></li>
 * </ul>
 *
<<<<<<< 2ac2f1c25f5911ee496ecf17224c763445337601
 * <p>The example below will match a mail with at most 3 executions on the mailet</p>
=======
 * <p>The example below will match mail with at most 3 tries on the mailet
 * with attribute name <i>AT_MOST_TRIES</i></p>
>>>>>>> j3295: handle configuration AtMostMatcher with differents config
 *
 * <pre><code>
 * &lt;mailet match=&quot;AtMost=AT_MOST_TRIES:3&quot; class=&quot;&lt;any-class&gt;&quot;&gt;
 * &lt;/mailet&gt;
 * </code></pre>
 */
public class AtMost extends GenericMatcher {
<<<<<<< 2ac2f1c25f5911ee496ecf17224c763445337601
    static final AttributeName AT_MOST_EXECUTIONS = AttributeName.of("AT_MOST_EXECUTIONS");
    private Integer atMostExecutions;

    @Override
    public void init() throws MessagingException {
        this.atMostExecutions = MailetUtil.getInitParameterAsStrictlyPositiveInteger(getCondition());
=======
    static final AttributeName AT_MOST_TRIES = AttributeName.of("AT_MOST_TRIES");
    private static final String CONDITION_SEPARATOR = ":";
    private static final int ONLY_CONDITION_VALUE = 1;
    private static final int CONDITION_NAME_AND_VALUE = 2;

    private Attribute condition;

    @Override
    public void init() throws MessagingException {
        String conditionConfig = getMatcherConfig().getCondition();
        Preconditions.checkArgument(StringUtils.isNotBlank(conditionConfig), "MatcherConfiguration is mandatory!");
        Preconditions.checkArgument(!conditionConfig.startsWith(CONDITION_SEPARATOR),
            "MatcherConfiguration can not start with '%s'", CONDITION_SEPARATOR);

        List<String> conditions = Splitter.on(CONDITION_SEPARATOR).splitToList(conditionConfig);
        condition = parseAttribute(conditions);
    }

    private Attribute parseAttribute(List<String> conditions) throws MessagingException {
        switch (conditions.size()) {
            case ONLY_CONDITION_VALUE:
                return new Attribute(AT_MOST_TRIES,
                    AttributeValue.of(MailetUtil.getInitParameterAsStrictlyPositiveInteger(conditions.get(0))));

            case CONDITION_NAME_AND_VALUE:
                return new Attribute(AttributeName.of(conditions.get(0)),
                    AttributeValue.of(MailetUtil.getInitParameterAsStrictlyPositiveInteger(conditions.get(1))));

            default:
                throw new IllegalArgumentException("MatcherConfiguration format should follow: 'value:name'");
        }
>>>>>>> j3295: handle configuration AtMostMatcher with differents config
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
<<<<<<< 2ac2f1c25f5911ee496ecf17224c763445337601
        return AttributeUtils.getValueAndCastFromMail(mail, AT_MOST_EXECUTIONS, Integer.class)
            .or(() -> Optional.of(0))
            .filter(executions -> executions < atMostExecutions)
            .map(executions -> {
                mail.setAttribute(new Attribute(AT_MOST_EXECUTIONS, AttributeValue.of(executions + 1)));
=======
        return AttributeUtils.getValueAndCastFromMail(mail, condition.getName(), Integer.class)
            .or(() -> Optional.of(0))
            .filter(retries -> retries < condition.getValue().valueAs(Integer.class).get())
            .map(retries -> {
                mail.setAttribute(new Attribute(condition.getName(), AttributeValue.of(retries + 1)));
>>>>>>> j3295: handle configuration AtMostMatcher with differents config
                return mail.getRecipients();
            })
            .orElse(ImmutableList.of());
    }
}
