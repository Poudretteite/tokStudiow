-- Każdy element zaliczenia ma własny rodzaj oceniania: ocena (2,0–5,0) albo zal/nzal.
-- Dotychczasowe elementy dostają skalę formy zajęć, do której należą.

ALTER TABLE assessment_items
    ADD COLUMN grading_scale_id UUID REFERENCES grading_scales (id);

UPDATE assessment_items a
SET grading_scale_id = cc.grading_scale_id
    FROM class_groups g
JOIN course_components cc ON cc.id = g.course_component_id
WHERE g.id = a.class_group_id;

ALTER TABLE assessment_items
    ALTER COLUMN grading_scale_id SET NOT NULL;