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
    'Data conflict': 'Ktoś zmienił te dane w międzyczasie. Odśwież stronę i spróbuj ponownie.',
};

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
