package org.codenova.moneylog.controller;

import lombok.AllArgsConstructor;
import org.codenova.moneylog.entity.Expense;
import org.codenova.moneylog.entity.User;
import org.codenova.moneylog.repository.CategoryRepository;
import org.codenova.moneylog.repository.ExpenseRepository;
import org.codenova.moneylog.vo.ExpenseJoinCategory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@AllArgsConstructor
@RequestMapping("/expense")
public class ExpenseController {

    private ExpenseRepository expenseRepository;
    private CategoryRepository categoryRepository;

    @GetMapping("/history")
    public String historyHandle(Model model, @SessionAttribute("user") User user) {

        model.addAttribute("sort", categoryRepository.findAll());
        model.addAttribute("now", LocalDate.now());
        model.addAttribute("expenses", expenseRepository.findByUserIdAndDuration(user.getId(), LocalDate.now().minusDays(10), LocalDate.now()));

        List<ExpenseJoinCategory> save = expenseRepository.expenseJoinCategory();
        model.addAttribute("save", save);



        return "expense/history";
    }

    @PostMapping("/history")
    public String expenseHandle(Model model, @ModelAttribute Expense expense,
                                 @SessionAttribute("user") User user){

        expense.setUserId(user.getId());
        expenseRepository.save(expense);


        return "redirect:/expense/history";
    }



}
