ALTER TABLE subscriptions
DROP CONSTRAINT subscriptions_pkey;

ALTER TABLE subscriptions
ADD PRIMARY KEY (user_id, repository_id, subscribed_to);