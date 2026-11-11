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
    weightPercent: number;
    orderIndex: number;
    plannedDate: string | null;
}

export interface PartialGrade {
    id: string;
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