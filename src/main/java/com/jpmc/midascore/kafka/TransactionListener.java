package com.jpmc.midascore.kafka;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.foundation.TransactionRecord;
import com.jpmc.midascore.repository.UserRepository;
import com.jpmc.midascore.repository.TransactionRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;



@Component
public class TransactionListener {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private TransactionRecordRepository transactionRecordRepository;

    @KafkaListener(topics = "${general.kafka-topic}", groupId = "midas-core")
    @Transactional
   public void listen(Transaction transaction) {
        UserRecord sender = userRepository.findById(transaction.getSenderId()).orElse(null);
        UserRecord recipient = userRepository.findById(transaction.getRecipientId()).orElse(null);

        if (sender == null || recipient == null) return;
        if (sender.getBalance() < transaction.getAmount()) return;

        Incentive incentive = restTemplate.postForObject(
            "http://localhost:8080/incentive",
            transaction,
            Incentive.class
        );
            float incentiveAmount = incentive != null ? incentive.getAmount() : 0f;


        sender.setBalance(sender.getBalance() - transaction.getAmount());
        recipient.setBalance(recipient.getBalance() + transaction.getAmount() + incentiveAmount);

        userRepository.save(sender);
        userRepository.save(recipient);

        TransactionRecord record = new TransactionRecord();
        record.setAmount(transaction.getAmount());
        record.setSender(sender);
        record.setRecipient(recipient);
        record.setIncentive(incentiveAmount);

        transactionRecordRepository.save(record);
    }
}
