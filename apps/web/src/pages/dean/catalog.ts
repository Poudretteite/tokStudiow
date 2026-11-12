import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { AcademicTerm, Course, FieldOfStudy } from '../../api/types';

/** Kierunki i przedmioty wydziału – do wyboru zajęć przy tworzeniu grupy. */
export function useFacultyCatalog(facultyId: string | null) {
    return useQuery({
        queryKey: ['faculties', facultyId, 'catalog'],
        enabled: facultyId !== null,
        queryFn: async () => {
            const fields = await api<FieldOfStudy[]>(`/api/faculties/${encodeURIComponent(facultyId!)}/fields-of-study`);
            const courses = await Promise.all(
                fields.map((f) => api<Course[]>(`/api/fields-of-study/${encodeURIComponent(f.id)}/courses`)),
            );
            return { fields, courses: courses.flat() };
        },
    });
}

export function useAcademicTerms() {
    return useQuery({ queryKey: ['academic-terms'], queryFn: () => api<AcademicTerm[]>('/api/academic-terms') });
}

/** Domyślnie bieżący semestr, a jeśli nie ustawiono – najnowszy. */
export function defaultTermId(terms: AcademicTerm[]): string | undefined {
    return (terms.find((t) => t.current) ?? [...terms].sort((a, b) => b.startDate.localeCompare(a.startDate))[0])?.id;
}
