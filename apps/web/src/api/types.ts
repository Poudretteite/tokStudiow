export type Role = 'STUDENT' | 'LECTURER' | 'DEAN_OFFICE' | 'ADMIN';
export type ComponentType = 'LECTURE' | 'EXERCISES' | 'LAB' | 'SEMINAR' | 'PROJECT';
export type FinalAttempt = 'FIRST' | 'SECOND' | 'COMMISSION';
export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED';
export type StudyStatus = 'ACTIVE' | 'DEAN_LEAVE' | 'GRADUATED' | 'REMOVED';

export interface Me {
    id: string;
    firstName: string;
    lastName: string;
    email: string;
    role: Role;
    albumNumber: string | null;
    facultyId: string | null;
    academicTitle: string | null;
}

export interface Study {
    id: string;
    fieldOfStudyName: string;
    startYear: number;
    currentSemester: number;
    status: StudyStatus;
}

export interface ClassGroup {
    id: string;
    courseId: string;
    courseCode: string;
    courseName: string;
    componentType: ComponentType;
    gradingScaleId: string;
    academicTermId: string;
    academicTermName: string;
    lecturerId: string;
    lecturerFirstName: string;
    lecturerLastName: string;
    groupNumber: number;
    capacity: number | null;
}

export interface AssessmentItem {
    id: string;
    name: string;
    gradingType: 'NUMERIC' | 'PASS_FAIL';
    gradingScaleId: string;
    weightPercent: number;
    orderIndex: number;
    plannedDate: string | null;
}

export interface PartialGrade {
    id: string;
    enrollmentId: string;
    studentId: string;
    studentFirstName: string;
    studentLastName: string;
    albumNumber: string;
    assessmentItemId: string;
    assessmentItemName: string;
    gradeLabel: string;
    numericValue: number | null;
    passing: boolean;
    attempt: number;
    issuedAt: string;
}

export interface MyAttendance {
    sessionId: string;
    date: string;
    startTime: string;
    endTime: string;
    status: AttendanceStatus;
}

export interface TranscriptEntry {
    courseCode: string;
    courseName: string;
    componentType: ComponentType;
    ects: number;
    academicTermName: string;
    attempt: FinalAttempt;
    gradeLabel: string;
    numericValue: number | null;
    passing: boolean;
    approvedAt: string | null;
}

export interface StudyTranscript {
    studyId: string;
    fieldOfStudyName: string;
    average: number | null;
    entries: TranscriptEntry[];
}

export interface RosterEntry {
    enrollmentId: string;
    studyId: string;
    studentId: string;
    firstName: string;
    lastName: string;
    albumNumber: string;
    enrolledAt: string;
}

export interface GradeValue {
    id: string;
    label: string;
    numericValue: number | null;
    passing: boolean;
}

export interface GradingScale {
    id: string;
    name: string;
    type: 'NUMERIC' | 'PASS_FAIL';
    values: GradeValue[];
}

export type FinalGradeStatus = 'DRAFT' | 'APPROVED';

export interface FinalGrade {
    id: string;
    enrollmentId: string;
    studentId: string;
    studentFirstName: string;
    studentLastName: string;
    albumNumber: string;
    attempt: FinalAttempt;
    status: FinalGradeStatus;
    gradeLabel: string;
    numericValue: number | null;
    passing: boolean;
    approvedBy: string | null;
    approvedAt: string | null;
}

/** Wiersz arkusza ocen końcowych; openAttempt = null oznacza rozliczony przedmiot. */
export interface FinalGradeSheetRow {
    enrollmentId: string;
    studentId: string;
    studentFirstName: string;
    studentLastName: string;
    albumNumber: string;
    weightedAverage: number | null;
    proposedGradeValueId: string | null;
    proposedGradeLabel: string | null;
    grades: FinalGrade[];
    openAttempt: FinalAttempt | null;
    draftGradeValueId: string | null;
}