package ru.coursework.rehab.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.coursework.rehab.domain.ChatMessage.Channel;
import ru.coursework.rehab.service.ChatService;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chat;
    public ChatController(ChatService chat) { this.chat=chat; }

    public record SendInput(@NotBlank @Size(max=2000) String text) {}
    public record ReadInput(@NotEmpty @Size(max=100) List<@NotNull @Positive Long> ids) {}

    @GetMapping("/threads") public List<ChatService.ThreadView> threads() { return chat.threads(); }
    @GetMapping("/{id}/{channel}") public ChatService.History history(@PathVariable Long id,@PathVariable Channel channel,
        @RequestParam(defaultValue="9223372036854775807") long before) { return chat.history(id,channel,before); }
    @PostMapping("/{id}/{channel}") @ResponseStatus(HttpStatus.CREATED)
    public ChatService.MessageView send(@PathVariable Long id,@PathVariable Channel channel,@Valid @RequestBody SendInput in) {
        return chat.send(id,channel,in.text());
    }
    @PatchMapping("/{id}/{channel}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable Long id,@PathVariable Channel channel,@Valid @RequestBody ReadInput in) {
        chat.read(id,channel,in.ids());
    }
}
