package vn.techflow.manager.task;
import io.swagger.v3.oas.annotations.Operation; import io.swagger.v3.oas.annotations.tags.Tag; import jakarta.validation.Valid; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.util.*;
@Tag(name="Tasks") @RestController @RequestMapping("/api/tasks") public class TaskController {
 private final TaskService service; public TaskController(TaskService s){service=s;}
 @Operation(summary="Danh sách công việc") @GetMapping public List<WorkTask> all(){return service.all();}
 @Operation(summary="Tạo công việc") @PostMapping @ResponseStatus(HttpStatus.CREATED) public WorkTask create(@Valid @RequestBody TaskRequest r){return service.save(new WorkTask(),r);}
 @Operation(summary="Cập nhật công việc") @PutMapping("/{id}") public WorkTask update(@PathVariable Long id,@Valid @RequestBody TaskRequest r){return service.save(service.get(id),r);}
 @Operation(summary="Xóa công việc") @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id){service.delete(id);}
 @Operation(summary="Tạo video nháp") @PostMapping("/{id}/generate") @ResponseStatus(HttpStatus.ACCEPTED) public Map<String,String> generate(@PathVariable Long id){service.get(id);service.generate(id);return Map.of("message","Đã đưa vào pipeline");}
}
