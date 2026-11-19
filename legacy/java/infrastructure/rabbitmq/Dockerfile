FROM rabbitmq:4.1.0-management
USER root
ADD https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/v4.1.0/rabbitmq_delayed_message_exchange-4.1.0.ez /plugins/rabbitmq_delayed_message_exchange-4.1.0.ez
RUN chmod 644 /plugins/rabbitmq_delayed_message_exchange-4.1.0.ez && rabbitmq-plugins enable --offline rabbitmq_delayed_message_exchange
USER rabbitmq
