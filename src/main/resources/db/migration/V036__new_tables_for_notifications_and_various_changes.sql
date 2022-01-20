DROP INDEX discount_bucket_code_used_idx;
CREATE INDEX discount_bucket_code_used_idx ON discount_bucket_code USING btree (discount_fk ASC, used ASC) WHERE used = false;

CREATE TABLE discount_bucket_code_summary
(
    discount_fk                    BIGINT    NOT NULL,
    available_codes                BIGINT    NOT NULL,
    expired_at                     TIMESTAMPTZ,

    CONSTRAINT discount_bucket_code_summary_pk PRIMARY KEY (discount_fk),
    CONSTRAINT discount_bucket_code_summary_fk FOREIGN KEY (discount_fk)
        REFERENCES discount (discount_k)
        ON UPDATE CASCADE
        ON DELETE CASCADE
);

INSERT INTO discount_bucket_code_summary
SELECT discount_id as discount_fk, COALESCE(SUM(number_of_codes), 0) as available_codes
FROM bucket_code_load
WHERE discount_id IN (SELECT DISTINCT discount_k FROM discount)
GROUP BY discount_id;

CREATE TABLE notification
(
    notification_k VARCHAR(255) NOT NULL,
    sent_at        TIMESTAMPTZ  NOT NULL,
    error_message  VARCHAR(255),

    CONSTRAINT notification_pk PRIMARY KEY (notification_k)
);

CREATE INDEX notification_key_idx ON notification (notification_k);


