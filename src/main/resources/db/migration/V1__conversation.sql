-- What the agent remembers about a resident between turns.
--
-- Two counters, and they count different things. turns is the conversation's
-- own: how many turns have been written with this resident, ever. version is
-- the row's, and belongs to the database: every write carries the value it read
-- in its WHERE clause, so two writes racing on one conversation cannot both
-- win. See Conversations.save.
CREATE TABLE conversation (
    user_id          VARCHAR(128)                NOT NULL,
    turns            INTEGER                     NOT NULL,
    version          INTEGER                     NOT NULL,
    last_seen        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    asked_domain     VARCHAR(32),
    asked_action     VARCHAR(32),
    asked_confidence DOUBLE PRECISION,
    asked_missing    VARCHAR(255),
    CONSTRAINT pk_conversation PRIMARY KEY (user_id)
);

-- What the resident has handed over. Its own table rather than a blob on the row
-- above: these are the only values in the system that identify a person, and they
-- are worth being able to find, count and delete on their own.
CREATE TABLE conversation_entity (
    user_id      VARCHAR(128) NOT NULL,
    entity_type  VARCHAR(32)  NOT NULL,
    entity_value VARCHAR(64)  NOT NULL,
    CONSTRAINT pk_conversation_entity PRIMARY KEY (user_id, entity_type),
    CONSTRAINT fk_conversation_entity_conversation FOREIGN KEY (user_id)
        REFERENCES conversation (user_id) ON DELETE CASCADE
);

-- Conversations are read by resident and swept by age, and nothing else.
CREATE INDEX ix_conversation_last_seen ON conversation (last_seen);
