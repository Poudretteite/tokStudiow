import { ApiError } from '../api/client';

const MESSAGES: Record<string, string> = {
    'Group not found': 'Nie znaleziono grupy.',
    'Enrollment not found': 'Ten student nie jest zapisany do grupy.',
    'Assessment item not found': 'Nie znaleziono elementu zaliczenia.',
    'Total weight of assessment items cannot exceed 100%': 'Suma wag elementów zaliczenia nie może przekroczyć 100%.',
    'Grade value does not belong to the grading scale of this course component':
        'Ta ocena nie należy do skali przypisanej do tej formy zajęć.',
    'Approved final grade cannot be changed': 'Zatwierdzonej oceny końcowej nie można zmienić.',
    'Previous attempt must be approved and failed':
        'Poprawkę można wpisać dopiero po zatwierdzonym i niezaliczonym poprzednim terminie.',
    'Session date must be within the academic term': 'Data zajęć musi mieścić się w semestrze.',
    'End time must be after start time': 'Godzina zakończenia musi być późniejsza niż godzina rozpoczęcia.',
    'Cannot record attendance before the session takes place': 'Obecność można wpisać dopiero po odbyciu się zajęć.',
    'Partial grade not found': 'Nie znaleziono oceny.',
    'Partial grades are locked after final grade approval':
        'Ocen cząstkowych nie można zmieniać po zatwierdzeniu oceny końcowej.',
    'Graded assessment items need a weight greater than 0': 'Element na ocenę musi mieć wagę większą od 0.',
    'Grade value does not belong to the grading scale of this assessment item':
        'Ta ocena nie pasuje do sposobu oceniania tego elementu.',
    'Each student may appear only once in the request': 'Każdy student może wystąpić w zapisie tylko raz.',
    'Final grade is already settled': 'Ten student ma już rozliczoną ocenę końcową.',
    'Data was changed in the meantime': 'Ktoś zmienił te dane w międzyczasie. Odśwież stronę i spróbuj ponownie.',
    'Session not found': 'Nie znaleziono zajęć.',
    'Cannot record attendance for a cancelled session': 'Nie można wpisać obecności na odwołanych zajęciach.',
    'Only active students can be enrolled': 'Zapisać można tylko aktywnych studentów.',
    "Course does not belong to the student's field of study": 'Przedmiot nie należy do kierunku studenta.',
    'Student is already enrolled in this group': 'Ten student jest już zapisany do tej grupy.',
    'Student is already enrolled in another group of this course component':
        'Ktoś z wybranych jest już zapisany do innej grupy tych zajęć. Nikogo nie zapisano.',
    'Group is full': 'Brak wolnych miejsc w grupie. Nikogo nie zapisano.',
    'Group with this number already exists': 'Grupa o tym numerze już istnieje w tym semestrze.',
    'Study not found': 'Nie znaleziono studenta.',
    'Semester exceeds the length of the field of study': 'Semestr przekracza liczbę semestrów kierunku.',
    'Final grade not found': 'Nie znaleziono oceny końcowej.',
    'Only approved final grades can be corrected': 'Korygować można tylko zatwierdzone oceny końcowe.',
    'Only the latest attempt can be corrected': 'Korygować można tylko ostatni termin studenta.',
    'Email already in use': 'Ten adres e-mail (login) jest już zajęty przez inne konto.',
    'Email is required for this role': 'Podaj adres e-mail – tylko studenci dostają go automatycznie.',
    'Album number already in use': 'Ten numer albumu jest już zajęty.',
    'Album number is required for students': 'Podaj numer albumu studenta.',
    'Faculty is required for this role': 'Wybierz wydział dla tej roli.',
    'Faculty not found': 'Nie znaleziono wydziału.',
    'User not found': 'Nie znaleziono użytkownika.',
    'You cannot deactivate your own account': 'Nie możesz zablokować własnego konta.',
    'Student not found': 'Nie znaleziono konta studenta.',
    'Field of study not found': 'Nie znaleziono kierunku.',
    'Student is already enrolled in this field of study': 'Ten student studiuje już na tym kierunku.',
    'Correction period has ended':
        'Termin poprawiania ocen minął (koniec sesji + 2 dni). Ocenę może teraz poprawić tylko dziekanat.',
    'Exam session must end within the academic term': 'Koniec sesji musi mieścić się w semestrze.',
    'Choose exactly one change: promotion or status': 'Wybierz jedną zmianę: przeniesienie na semestr albo status.',
    'Some rows cannot be imported': 'Nie zaimportowano nikogo – popraw wiersze zaznaczone na czerwono.',
    'Duplicate email in import': 'Inny wiersz w pliku daje ten sam login (e-mail).',
    'Duplicate album number in import': 'Ten sam numer albumu jest w pliku więcej niż raz.',
    'Current password is incorrect': 'Obecne hasło jest nieprawidłowe.',
    'Too many attempts': 'Zbyt wiele nieudanych prób. Ze względów bezpieczeństwa spróbuj ponownie za kilka minut.',
    'New password must differ from the current one': 'Nowe hasło musi różnić się od obecnego.',
    'Password must have at least 12 characters': 'Hasło musi mieć co najmniej 12 znaków.',
    'Password is too long': 'Hasło jest za długie (maks. 72 bajty, ok. 70 znaków).',
    'Password must not contain your name or login': 'Hasło nie może zawierać imienia, nazwiska ani loginu.',
    'Password is too easy to guess': 'To hasło łatwo odgadnąć – użyj kilku niepowiązanych słów.',
    'You cannot reset your own password': 'Własne hasło zmień w „Zmień hasło”, podając obecne.',
    'Account is deactivated': 'Konto jest zablokowane – nie można nadać mu hasła.',
    'Schedule conflict': 'Nie dodano terminu – koliduje z innymi zajęciami.',
    'Room is too small for this group': 'Sala jest za mała dla tej grupy.',
    'Classes must end after they start': 'Koniec zajęć musi być po ich początku.',
    'Classes must fit between 7:00 and 21:00': 'Zajęcia muszą mieścić się między 7:00 a 21:00.',
    'Times must be multiples of 5 minutes': 'Godziny podawaj co 5 minut.',
    'Day of week must be between 1 and 7': 'Wybierz dzień tygodnia.',
    'Schedule entry not found': 'Nie znaleziono terminu – mógł zostać już usunięty.',
    'Room not found': 'Nie znaleziono sali.',
    'Data conflict': 'Ktoś zmienił te dane w międzyczasie. Odśwież stronę i spróbuj ponownie.',
};

/** Komunikat z serwera (np. błąd jednego wiersza importu) po polsku; nieznany – bez zmian. */
export function messageText(message: string): string {
    return MESSAGES[message] ?? message;
}

/** Komunikat dla użytkownika: znany błąd po polsku, inne – ogólny opis bez szczegółów technicznych. */
export function errorMessage(error: unknown): string {
    if (error instanceof ApiError) {
        if (MESSAGES[error.message]) {
            return MESSAGES[error.message];
        }
        if (error.status === 403 || error.status === 404) {
            return 'Nie masz dostępu do tych danych.';
        }
        if (error.status === 400) {
            return 'Sprawdź poprawność wprowadzonych danych.';
        }
    }
    return 'Nie udało się zapisać zmian. Spróbuj ponownie.';
}
