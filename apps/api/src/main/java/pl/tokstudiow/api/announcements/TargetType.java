package pl.tokstudiow.api.announcements;

/** Do kogo trafia ogłoszenie. */
public enum TargetType {
    /** studenci grupy (piszą prowadzący grupy i dziekanat jej wydziału) */
    GROUP,
    /** studenci kierunku (dziekanat wydziału) */
    FIELD_OF_STUDY,
    /** studenci i prowadzący wydziału (dziekanat wydziału) */
    FACULTY,
    /** cała uczelnia (administrator) */
    ALL
}
