package com.cashflow.auth;

import com.cashflow.common.ApiException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailDelivery {
    private final JavaMailSender sender;
    private final String mode,from,directory,frontend;
    public MailDelivery(JavaMailSender sender,@Value("${app.mail-mode}") String mode,
        @Value("${app.mail-from}") String from,@Value("${app.mail-directory}") String directory,
        @Value("${app.frontend-url}") String frontend,@Value("${app.production}") boolean production) {
        if(production && !"smtp".equals(mode)) throw new IllegalStateException("Production requires SMTP delivery.");
        this.sender=sender; this.mode=mode; this.from=from; this.directory=directory; this.frontend=frontend;
    }
    public void send(String email,String kind,String token) {
        String subject="VERIFY".equals(kind) ? "Verify your CashFlow account" : "Reset your CashFlow password";
        String route="VERIFY".equals(kind) ? "/verify-email" : "/reset-password";
        // Fragment avoids leaking the token into access logs and Referer headers.
        String body=subject+"\n\n"+frontend+route+"#token="+token+"\n\nIf you did not request this, you can ignore this message.";
        try {
            if("file".equals(mode)) {
                Path folder=Path.of(directory); Files.createDirectories(folder);
                Files.writeString(folder.resolve(UUID.randomUUID()+".eml"),"To: "+email+"\nSubject: "+subject+"\n\n"+body,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            } else {
                var message=new SimpleMailMessage(); message.setFrom(from); message.setTo(email); message.setSubject(subject); message.setText(body); sender.send(message);
            }
        } catch(Exception e) { throw new ApiException(503,"Email service unavailable. Please retry shortly."); }
    }
}
