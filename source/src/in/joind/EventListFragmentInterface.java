package in.joind;

public interface EventListFragmentInterface {
    void setEventSortOrder(int sortOrder);

    int getEventSortOrder();

    void filterByString(CharSequence s);
}