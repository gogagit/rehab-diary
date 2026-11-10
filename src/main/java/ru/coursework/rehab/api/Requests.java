package ru.coursework.rehab.api;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import ru.coursework.rehab.domain.*;
public final class Requests {
    private Requests() {}
    public record RegistrationInput(
        @NotBlank @Email(message="укажите корректный email") @Size(max=254) String username,
        @NotBlank @Size(max=150) String fullName,
        @NotBlank @Size(min=10,max=72) String password,
        @NotBlank @Size(max=72) String passwordConfirmation,
        @NotNull @Past LocalDate birthDate,
        @Size(max=200) String rehabilitationReason) {}
    public record SpecialistInput(
        @NotBlank @Email(message="укажите корректный email") @Size(max=254) String username,
        @NotBlank @Size(max=150) String fullName,
        @NotBlank @Size(min=10,max=72) String password,
        @NotBlank @Size(max=72) String passwordConfirmation) {}
    public record SpecialistAssignment(Long specialistId) {}
    public record PatientInput(
        @NotBlank @Email(message="укажите корректный email") @Size(max=254) String username,
        @NotBlank @Size(min=10,max=72) String password,
        @NotBlank @Size(max=150) String fullName,
        @NotNull @Past LocalDate birthDate,
        @NotBlank @Size(max=200) String rehabilitationReason,
        @Size(max=2000) String notes, @NotNull Long specialistId) {}
    public record ReviewInput(@NotBlank @Size(max=1000) String text) {}
    public record ExerciseInput(@NotBlank @Size(max=120) String title,
        @NotBlank @Size(max=2000) String instructions,@NotNull Exercise.Unit unit) {}
    public record PlanInput(@NotBlank @Size(max=150) String title,
        @NotBlank @Size(max=1000) String goal,@NotNull LocalDate startsOn,@NotNull LocalDate endsOn) {}
    public record AssignmentInput(@NotNull Long exerciseId,@NotNull @DecimalMin("0.01") @DecimalMax("100000") Double targetAmount) {}
    public record EntryInput(@NotNull Long assignmentId,@NotNull LocalDate performedOn,
        @NotNull DiaryEntry.Status status,@NotNull @DecimalMin("0") @DecimalMax("100000") Double actualAmount,
        @Min(0) @Max(10) int pain,@Min(1) @Max(10) int wellbeing,
        @NotNull @Size(max=2000) String comment,Long version) {}
    public record FeedbackInput(@NotBlank @Size(max=2000) String text,@NotNull Long version) {}
    public record StatusInput(@NotNull RehabPlan.Status status,@NotNull Long version) {}
}
