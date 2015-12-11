/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.webbrar.wurm.mods.bulktransportmod;

import com.wurmonline.server.FailedException;
import com.wurmonline.server.Items;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.NoSuchPlayerException;
import com.wurmonline.server.behaviours.MethodsItems;
import com.wurmonline.server.creatures.NoSuchCreatureException;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.NoSuchTemplateException;
import com.wurmonline.server.questions.RemoveItemQuestion;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.lang.reflect.Field;


import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmMod;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;

import javassist.CtClass;
import javassist.NotFoundException;
import javassist.CtPrimitiveType;
import javassist.bytecode.Descriptor;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
/**
 *
 * @author Webba
 */
public class BulkTransportMod implements WurmMod, Configurable{
    private Logger logger = Logger.getLogger(this.getClass().getName());
    private static BulkTransportMod instance;
    
    public static BulkTransportMod getInstance(){
        if (instance == null){
            instance = new BulkTransportMod();
        }
        return instance;
    }
    
    public BulkTransportMod(){
        BulkTransportMod.instance = this;
    }
    
    public Logger getLogger(){        
        return this.logger;
    }
    
    
    @Override
    public void configure(Properties properties) {

    logger.log(Level.INFO, "Bulk transport");
        try {
            CtClass[] paramTypes = {
                HookManager.getInstance().getClassPool().getCtClass("java.util.Properties")
            };

            HookManager.getInstance().registerHook("com.wurmonline.server.questions.RemoveItemQuestion", "answer", Descriptor.ofMethod(CtPrimitiveType.voidType, paramTypes), new InvocationHandlerFactory(){
                        @Override 
                        public InvocationHandler createInvocationHandler(){
                            return new InvocationHandler(){

                                @Override
                                public Object invoke(Object object, Method method, Object[] args) throws Throwable {
                                    RemoveItemQuestion question = (RemoveItemQuestion)object;
                                    Properties aAnswers = (Properties)args[0];
                                    final String numstext = aAnswers.getProperty("numstext");
                                    String nums = aAnswers.getProperty("items");
                                    if (numstext != null && numstext.length() > 0) {
                                        nums = numstext;
                                    }
                                    if (nums != null && nums.length() > 0) {
                                        if (nums.equals("1000")) {
                                            question.getResponder().getCommunicator().sendNormalServerMessage("You selected max.");
                                        }
                                        else {
                                            question.getResponder().getCommunicator().sendNormalServerMessage("You selected " + nums + ".");
                                        }
                                        try {
                                            int i = Integer.parseInt(nums);
                                            try {
                                                final Item bulkitem = Items.getItem(question.getTarget());
                                                if (!question.getResponder().isWithinDistanceTo(bulkitem.getPosX(), bulkitem.getPosY(), bulkitem.getPosZ(), 4.0f)) {
                                                    question.getResponder().getCommunicator().sendNormalServerMessage("You are too far away from the " + bulkitem.getName() + " now.");
                                                    return null;
                                                }
                                                boolean full = false;
                                                Item parent = null;
                                                try {
                                                    parent = bulkitem.getParent();
                                                    full = parent.isFull();
                                                }
                                                catch (NoSuchItemException ex) {}
                                                final boolean max = i == 1000;
                                                int bnums = 0;
                                                if (bulkitem.getRealTemplate() != null && bulkitem.getRealTemplate().isCombine()) {
                                                    bnums = (int)Math.ceil(bulkitem.getBulkNumsFloat(false));
                                                }
                                                else {
                                                    bnums = bulkitem.getBulkNums();
                                                }
                                                Item toInsert = null;
                                                final int current = question.getResponder().getInventory().getNumItemsNotCoins();
                                                final int maxCapac = Math.max(0, 100 - current);
                                                Item targetInventory = null;
                                                Field moveTarget = question.getClass().getDeclaredField("moveTarget");
                                                moveTarget.setAccessible(true);
                                                final long mTarg = (long)moveTarget.get(question);
                                                moveTarget.setAccessible(false);     
                                                try {
                                                    if (mTarg != 0L) {
                                                        targetInventory = Items.getItem(mTarg);
                                                    }
                                                }
                                                catch (NoSuchItemException nsi) {
                                                    final String message = String.format("Unable to find item: %s.", Long.valueOf(mTarg).toString());
                                                    BulkTransportMod.getInstance().getLogger().log(Level.WARNING, message, (Throwable)nsi);
                                                    return null;
                                                }
                                                if ((i > maxCapac) && (mTarg == 0L || mTarg == question.getResponder().getInventory().getWurmId())) {
                                                    i = Math.min(bnums, maxCapac);
                                                }
                                                
                                                if (bnums < i && i != 1000) {
                                                    question.getResponder().getCommunicator().sendNormalServerMessage("The " + bulkitem.getName() + " does not contain " + i + " items. Moving " + bnums + " instead.");
                                                    i = Math.min(bnums, i);
                                                }
                                                int weightReduced = 0;
                                                final ItemTemplate template = bulkitem.getRealTemplate();
                                                if (template != null){
                                                    final int volume = template.getVolume();
                                                    if (max && (mTarg == 0L || mTarg == question.getResponder().getInventory().getWurmId())) {
                                                        if(!targetInventory.isBulkContainer()){
                                                            i = Math.min(maxCapac, question.getResponder().getCarryCapacityFor(template.getWeightGrams()));
                                                        }
                                                        if (i <= 0) {
                                                            question.getResponder().getCommunicator().sendNormalServerMessage("You can not even carry one of those.");
                                                            return null;
                                                        }
                                                        i = Math.min(i, bnums);
                                                    }
                                                    else if (!question.getResponder().canCarry(template.getWeightGrams() * i) && (i > 1 || bulkitem.getWeightGrams() >= volume) && (mTarg == 0L || mTarg == question.getResponder().getInventory().getWurmId())) {
                                                        question.getResponder().getCommunicator().sendNormalServerMessage("You may not carry that weight.");
                                                        return null;
                                                    }
                                                    if(targetInventory.isBulkContainer()){
                                                        float toBulkInsert = 0;
                                                        int maxAllowed = 0; 
                                                        if(targetInventory.isCrate()){
                                                            maxAllowed = targetInventory.getRemainingCrateSpace();
                                                            toBulkInsert = Math.min(maxAllowed, i);
                                                        }
                                                        else
                                                        {
                                                            int usedVolume = 0;
                                                            try{
            
                                                                Method m = ReflectionUtil.getMethod(Item.class, "getUsedVolume");
                                                                Object f = ReflectionUtil.callPrivateMethod(targetInventory, m, new Object[]{});
                                                                usedVolume = (int)f;
                                                            }
                                                            catch(Exception ex){
                                                            }
                                                            float val = (targetInventory.getContainerVolume() - usedVolume)/ template.getVolume();
                                                            toBulkInsert = Math.min(val, i);
                                                        }
                                                        toBulkInsert = Math.min(bulkitem.getBulkNumsFloat(false), toBulkInsert);
                                                        toInsert = ItemFactory.createItem(bulkitem.getRealTemplateId(), bulkitem.getCurrentQualityLevel(), bulkitem.getMaterial(), (byte)0, question.getResponder().getName());
                                                        toInsert.setLastOwnerId(question.getResponder().getWurmId());
                                                        if (toInsert.isRepairable()) {
                                                            final byte newState = MethodsItems.getNewCreationState(toInsert.getMaterial());
                                                            toInsert.setCreationState(newState);
                                                        }
                                                        int insertWeight = (int)(toBulkInsert * template.getWeightGrams());
                                                        toInsert.setWeight(insertWeight, true);
                                                        Label_Create:{
                                                            try {
                                                                if ((targetInventory.isCrate() || !targetInventory.hasSpaceFor(toInsert.getVolume())) && (!targetInventory.isCrate() || !targetInventory.canAddToCrate(toInsert))) {
                                                                    question.getResponder().getCommunicator().sendNormalServerMessage(String.format("The %s will not fit in the %s.", toInsert.getName(), targetInventory.getName()));
                                                                    Items.destroyItem(toInsert.getWurmId());
                                                                    break Label_Create;
                                                                }
                                                                if (!toInsert.moveToItem(question.getResponder(), targetInventory.getWurmId(), false)) {
                                                                    Items.destroyItem(toInsert.getWurmId());
                                                                    break Label_Create;
                                                                }
                                                                break Label_Create;
                                                            }
                                                            catch (NoSuchPlayerException ex2) {
                                                                break Label_Create;
                                                            }
                                                            catch (NoSuchCreatureException ex3) {
                                                                break Label_Create;
                                                            }
                                                        }
                                                        weightReduced = (int)(toBulkInsert * template.getVolume());
                                                    }
                                                    else{
                                                        for (int created = 0; created < i; ++created) {
                                                            try {
                                                                int weight = bulkitem.getWeightGrams() - weightReduced;
                                                                float percent = 1.0f;
                                                                if (weight < volume) {
                                                                    percent = (float)weight / volume;
                                                                }
                                                                else {
                                                                    weight = Math.min(bulkitem.getWeightGrams(), volume);
                                                                }
                                                                if (weight > 0) {
                                                                    toInsert = ItemFactory.createItem(bulkitem.getRealTemplateId(), bulkitem.getCurrentQualityLevel(), bulkitem.getMaterial(), (byte)0, question.getResponder().getName());
                                                                    toInsert.setLastOwnerId(question.getResponder().getWurmId());
                                                                    if (toInsert.isRepairable()) {
                                                                        final byte newState = MethodsItems.getNewCreationState(toInsert.getMaterial());
                                                                        toInsert.setCreationState(newState);
                                                                    }
                                                                    toInsert.setWeight((int)(percent * template.getWeightGrams()), true);
                                                                    Label_0855: {
                                                                        if (mTarg == 0L) {
                                                                            question.getResponder().getInventory().insertItem(toInsert);
                                                                        }
                                                                        else {
                                                                            if (targetInventory.isBulkContainer()) {
                                                                                try {
                                                                                    if ((targetInventory.isCrate() || !targetInventory.hasSpaceFor(toInsert.getVolume())) && (!targetInventory.isCrate() || !targetInventory.canAddToCrate(toInsert))) {
                                                                                        final String message2 = "The %s will not fit in the %s.";
                                                                                        question.getResponder().getCommunicator().sendNormalServerMessage(String.format("The %s will not fit in the %s.", toInsert.getName(), targetInventory.getName()));
                                                                                        Items.destroyItem(toInsert.getWurmId());
                                                                                        break;
                                                                                    }
                                                                                    if (!toInsert.moveToItem(question.getResponder(), targetInventory.getWurmId(), false)) {
                                                                                        Items.destroyItem(toInsert.getWurmId());
                                                                                        break;
                                                                                    }
                                                                                    break Label_0855;
                                                                                }
                                                                                catch (NoSuchPlayerException ex2) {
                                                                                    break Label_0855;
                                                                                }
                                                                                catch (NoSuchCreatureException ex3) {
                                                                                    break Label_0855;
                                                                                }
                                                                            }
                                                                            if (!targetInventory.testInsertItem(toInsert) || !targetInventory.mayCreatureInsertItem()) {
                                                                                final String message2 = "There is not enough space for any more items.";
                                                                                question.getResponder().getCommunicator().sendNormalServerMessage("There is not enough space for any more items.");
                                                                                Items.destroyItem(toInsert.getWurmId());
                                                                                break;
                                                                            }
                                                                            targetInventory.insertItem(toInsert);
                                                                        }
                                                                    }
                                                                    weightReduced += weight;
                                                                }
                                                            }
                                                            catch (NoSuchTemplateException nst) {
                                                                BulkTransportMod.getInstance().getLogger().log(Level.WARNING, nst.getMessage(), (Throwable)nst);
                                                            }
                                                            catch (FailedException fe) {
                                                                BulkTransportMod.getInstance().getLogger().log(Level.WARNING, fe.getMessage(), (Throwable)fe);
                                                            }
                                                        }
                                                    }
                                                    question.getResponder().achievement(167, -i);
                                                    bulkitem.setWeight(bulkitem.getWeightGrams() - weightReduced, true);
                                                }
                                                if (parent != null && (full != parent.isFull() || parent.isCrate())) {
                                                    parent.updateModelNameOnGroundItem();
                                                }
                                            }
                                            catch(NoSuchItemException ni){
                                                question.getResponder().getCommunicator().sendNormalServerMessage("No such item.");
                                            }
                                        }
                                        catch(NumberFormatException ne){
                                            question.getResponder().getCommunicator().sendNormalServerMessage("Not a number.");
                                        }
                                    }
                                    return null;
                                }
                            };
                        }
                    });
        } 
        catch (Exception e) {
            throw new HookException(e);
        }
    }
}
