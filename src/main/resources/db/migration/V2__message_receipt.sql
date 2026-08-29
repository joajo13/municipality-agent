-- Messages that have already been handled, and the answer they were given.
--
-- Providers redeliver: a timeout on their side, a retry policy, an acknowledgement
-- that was lost rather than a request. The primary key is what makes the second
-- delivery a lookup instead of a second turn, and the stored response is what makes
-- it the same answer rather than a fresh one.
CREATE TABLE message_receipt (
    message_id  VARCHAR(128)                NOT NULL,
    user_id     VARCHAR(128)                NOT NULL,
    received_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    response    VARCHAR(8192)               NOT NULL,
    CONSTRAINT pk_message_receipt PRIMARY KEY (message_id)
);

-- Receipts are looked up by id and swept by age, and nothing else.
CREATE INDEX ix_message_receipt_received_at ON message_receipt (received_at);
