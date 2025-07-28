package com.example.overlay_controller;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

public class CustomButtonAdapter extends RecyclerView.Adapter<CustomButtonAdapter.ViewHolder> {

    private List<CustomButtonConfig> buttonConfigs;
    private final LayoutInflater inflater;
    private final OnButtonConfigActionListener listener;

    // MainActivity(또는 Fragment)에서 구현하여 수정/삭제 이벤트를 받을 인터페이스
    public interface OnButtonConfigActionListener {
        void onEditConfig(CustomButtonConfig config, int position);
        void onDeleteConfig(CustomButtonConfig config, int position);
    }

    public CustomButtonAdapter(Context context, List<CustomButtonConfig> buttonConfigs, OnButtonConfigActionListener listener) {
        this.inflater = LayoutInflater.from(context);
        this.buttonConfigs = buttonConfigs; // 초기 데이터 설정
        this.listener = listener;
    }

    // 새로운 ViewHolder(아이템 뷰를 감싸는 객체)를 생성
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // item_custom_button.xml 레이아웃을 인플레이트하여 뷰 생성
        View view = inflater.inflate(R.layout.item_custom_button, parent, false);
        return new ViewHolder(view);
    }

    // ViewHolder에 데이터를 바인딩 (실제 데이터를 뷰에 표시)
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CustomButtonConfig config = buttonConfigs.get(position); // 현재 위치의 데이터 가져오기

        // 데이터 설정
        holder.labelTextView.setText(config.getLabel());
        holder.keyNameTextView.setText(String.format("키: %s", config.getKeyName()));
        holder.propertiesTextView.setText(
                String.format(Locale.getDefault(), "위치: (%.2f, %.2f), 크기: (%.2f, %.2f)",
                        config.getXPositionPercent(), config.getYPositionPercent(),
                        config.getWidthPercent(), config.getHeightPercent())
        );

        // 수정 버튼 클릭 리스너 설정
        holder.editButton.setOnClickListener(v -> {
            if (listener != null) {
                // getAdapterPosition()은 RecyclerView 내의 현재 아이템 위치를 반환
                listener.onEditConfig(config, holder.getAdapterPosition());
            }
        });

        // 삭제 버튼 클릭 리스너 설정
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteConfig(config, holder.getAdapterPosition());
            }
        });
    }

    // 전체 아이템 개수 반환
    @Override
    public int getItemCount() {
        return buttonConfigs == null ? 0 : buttonConfigs.size();
    }

    // 데이터를 업데이트하고 RecyclerView에 변경 사항을 알리는 메소드
    public void updateData(List<CustomButtonConfig> newConfigs) {
        this.buttonConfigs = newConfigs;
        notifyDataSetChanged(); // 가장 간단한 전체 새로고침.
        // 더 효율적인 방법으로 notifyItemInserted, notifyItemRemoved, notifyItemChanged 등을 사용할 수 있음.
    }

    // 특정 위치의 아이템을 가져오는 메소드 (필요시 사용)
    public CustomButtonConfig getItem(int position) {
        if (buttonConfigs != null && position >= 0 && position < buttonConfigs.size()) {
            return buttonConfigs.get(position);
        }
        return null;
    }

    // 각 아이템 뷰의 구성요소를 보관하는 ViewHolder 클래스
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView labelTextView;
        TextView keyNameTextView;
        TextView propertiesTextView;
        ImageButton editButton;
        ImageButton deleteButton;

        ViewHolder(View itemView) {
            super(itemView); // 부모 생성자 호출
            // 레이아웃의 ID를 통해 뷰 참조 초기화
            labelTextView = itemView.findViewById(R.id.textView_button_label);
            keyNameTextView = itemView.findViewById(R.id.textView_button_key_name);
            propertiesTextView = itemView.findViewById(R.id.textView_button_properties);
            editButton = itemView.findViewById(R.id.button_edit_config);
            deleteButton = itemView.findViewById(R.id.button_delete_config);
        }
    }
}
