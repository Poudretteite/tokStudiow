import type { AttendanceStatus, ComponentType, FinalAttempt, Role, StudyStatus } from '../api/types';

export const ROLE_LABELS: Record<Role, string> = {
    STUDENT: 'Student',
    LECTURER: 'Prowadzący',
    DEAN_OFFICE: 'Dziekanat',
    ADMIN: 'Administrator',
};

export const COMPONENT_LABELS: Record<ComponentType, string> = {
    LECTURE: 'Wykład',
    EXERCISES: 'Ćwiczenia',
    LAB: 'Laboratorium',
    SEMINAR: 'Seminarium',
    PROJECT: 'Projekt',
};

export const ATTEMPT_LABELS: Record<FinalAttempt, string> = {
    FIRST: 'I termin',
    SECOND: 'Poprawka',
    COMMISSION: 'Komisja',
};

export const ATTENDANCE_LABELS: Record<AttendanceStatus, string> = {
    PRESENT: 'Obecny',
    ABSENT: 'Nieobecny',
    LATE: 'Spóźniony',
    EXCUSED: 'Usprawiedliwiony',
};

export const ATTENDANCE_CLASSES: Record<AttendanceStatus, string> = {
    PRESENT: 'text-green-700',
    ABSENT: 'text-red-700',
    LATE: 'text-amber-700',
    EXCUSED: 'text-blue-700',
};

export const STUDY_STATUS_LABELS: Record<StudyStatus, string> = {
    ACTIVE: 'aktywny',
    DEAN_LEAVE: 'urlop dziekański',
    GRADUATED: 'absolwent',
    REMOVED: 'skreślony',
};